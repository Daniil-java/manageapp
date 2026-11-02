package com.kuklin.manageapp.bots.caloriebot.bot;

import com.kuklin.manageapp.aiconversation.models.AiUnavailableException;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.web.error.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.web.error.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.support.telegram.SupportInputUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.payment.RefundedPaymentCapture;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.TelegramFacade;
import com.kuklin.manageapp.common.telegram.UpdateHandler;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Component
@Slf4j
public class TelegramCalorieBotFacade extends TelegramFacade {
    /** callback_data кнопок-подписей, которые ничего не делают («Вес порции», шкалы на экране «Сегодня»). */
    public static final String NO_ACTION_CALLBACK = TelegramKeyboard.NO_ACTION;

    @Autowired
    private TelegramUserService telegramUserService;
    @Autowired
    private CalorieMenuButtons menuButtons;
    @Autowired
    private UserSettingsService userSettingsService;
    @Autowired
    private CalorieTexts texts;
    @Lazy
    @Autowired
    private SupportInputUpdateHandler supportInputUpdateHandler;
    // Бот сам зависит от фасада — берём его лениво
    @Lazy
    @Autowired
    private CalorieTelegramBot calorieTelegramBot;

    @Override
    public void handleUpdate(Update update) {
        if (!update.hasCallbackQuery()
                && !update.hasMessage()
                && !update.hasPreCheckoutQuery()) return;

        User user = update.hasMessage() ?
                update.getMessage().getFrom() :
                update.hasPreCheckoutQuery()
                        ? update.getPreCheckoutQuery().getFrom()
                        : update.getCallbackQuery().getFrom();

        TelegramUser telegramUser = telegramUserService
                .createOrGetUserByTelegram(BotIdentifier.CALORIE_BOT, user);
        // Настройки создаём явно здесь, а не лениво при чтении часового пояса: по строке user_settings
        // шедулеры решают, кому слать ежедневный отчёт и напоминания. У существующих — один поиск по PK.
        userSettingsService.getOrCreate(telegramUser.getAppUserId());

        // Описание заявки в поддержку или ответ поддержке — не еда
        UpdateHandler updateHandler = supportInputUpdateHandler.isSupportInput(update, telegramUser)
                ? supportInputUpdateHandler
                : processInputUpdate(update);
        // null — только у незнакомых кнопок и кнопок-подписей: их нажатие ничего не делает
        if (updateHandler == null) {
            log.debug("No suitable handler found, no reply will be sent");
        } else {
            handleOrNotifyError(updateHandler, update, telegramUser);
        }
    }

    /**
     * Исключение из любого обработчика — отвечаем пользователю, а не молчим: раньше оно уходило
     * в пул обработки апдейтов и только логировалось.
     */
    private void handleOrNotifyError(UpdateHandler updateHandler, Update update, TelegramUser telegramUser) {
        try {
            updateHandler.handle(update, telegramUser);
        } catch (RuntimeException e) {
            notifyError(updateHandler, update, telegramUser, e);
        }
    }

    /**
     * Оплата — свои ответы: деньги уже списаны или счёт ждёт ответа, «попробуйте позже» не подходит.
     * Остальное: ИИ не ответил (попытку фото уже вернул FeatureAccessAspect) — AI_UNAVAILABLE;
     * ErrorResponseException из сервиса — его текст из errors_*; всё прочее (БД, баг) — общий ответ с /support.
     */
    private void notifyError(UpdateHandler updateHandler, Update update, TelegramUser telegramUser, RuntimeException e) {
        Long appUserId = telegramUser.getAppUserId();
        String handlerName = updateHandler.getClass().getSimpleName();
        if (update.hasPreCheckoutQuery()) {
            log.error("Pre-checkout failed in {} for user {}", handlerName, appUserId, e);
            answerPreCheckoutFailed(update.getPreCheckoutQuery().getId(), appUserId);
            return;
        }
        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            log.error("Successful payment not handled in {} for user {}", handlerName, appUserId, e);
            calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(),
                    texts.error(appUserId, ErrorStatus.PAYMENT_NOT_ACTIVATED));
            return;
        }
        if (RefundedPaymentCapture.findOrNull(update) != null) {
            // Возврат пользователю ответа не требует — нужен только алерт
            log.error("Refunded payment not handled in {} for user {}", handlerName, appUserId, e);
            return;
        }

        String text;
        if (e instanceof AiUnavailableException) {
            log.warn("AI unavailable for user {}: {}", appUserId, e.getMessage());
            text = texts.error(appUserId, ErrorStatus.AI_UNAVAILABLE);
        } else if (e instanceof ErrorResponseException errorResponse) {
            // Ожидаемая ошибка с понятным текстом — без алерта
            log.warn("{} for user {}: {}", handlerName, appUserId, e.getMessage());
            text = texts.error(appUserId, errorResponse);
        } else {
            log.error("Unhandled error in {} for user {}", handlerName, appUserId, e);
            text = texts.error(appUserId, ErrorStatus.UNEXPECTED_ERROR);
        }
        Long chatId = getChatIdOrNull(update);
        if (chatId == null) return;
        calorieTelegramBot.sendReturnedMessage(chatId, text, menuButtons.buildKeyboard(appUserId), null);
    }

    /** Без ответа на pre-checkout Telegram держит оплату до таймаута — отказываем сразу. */
    private void answerPreCheckoutFailed(String preCheckoutQueryId, Long appUserId) {
        AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
        answer.setPreCheckoutQueryId(preCheckoutQueryId);
        answer.setOk(false);
        answer.setErrorMessage(texts.error(appUserId, ErrorStatus.UNEXPECTED_ERROR));
        try {
            calorieTelegramBot.execute(answer);
        } catch (TelegramApiException ex) {
            log.error("Failed to answer pre-checkout query {}", preCheckoutQueryId, ex);
        }
    }

    private static Long getChatIdOrNull(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }

    public UpdateHandler processInputUpdate(Update update) {
        // 1. Платежи и чеки (самый высокий приоритет)
        if (update.hasPreCheckoutQuery()) {
            return getHandler(Command.PAYMENT_PRE_CHECK_QUERY);
        }

        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            return getHandler(Command.PAYMENT_SUCCESS);
        }

        if (RefundedPaymentCapture.findOrNull(update) != null) {
            return getHandler(Command.PAYMENT_REFUNDED);
        }

        // 2. Распределяем по типам контента
        if (update.hasCallbackQuery()) {
            return handleCallbackQuery(update.getCallbackQuery());
        }

        if (update.hasMessage()) {
            return handleMessage(update.getMessage());
        }

        return getHandler(Command.CALORIE_GENERAL);
    }

    private UpdateHandler handleCallbackQuery(CallbackQuery query) {
        String data = query.getData();
        if (data == null) return getHandler(Command.CALORIE_GENERAL);

        // Проверка префиксов
        if (data.startsWith(Command.CALORIE_FAVORITE.getCommandText())) return getHandler(Command.CALORIE_FAVORITE);
        if (data.startsWith(Command.CALORIE_PROFILE.getCommandText())) return getHandler(Command.CALORIE_PROFILE);
        if (data.startsWith(Command.CALORIE_WATER.getCommandText()))   return getHandler(Command.CALORIE_WATER);

        // Извлечение команды по разделителю
        String commandKey = data.split(TelegramBot.DEFAULT_DELIMETER)[0];
        UpdateHandler handler = getUpdateHandlerMap().get(commandKey);

        // Незнакомая кнопка (старое сообщение, переименованная команда) — ничего не делаем.
        // Раньше такие уходили в удаление блюда: «xxx 123» удаляла блюдо 123
        if (handler == null && !NO_ACTION_CALLBACK.equals(data)) {
            log.warn("Unknown callback data: {}", data);
        }
        return handler;
    }

    private UpdateHandler handleMessage(Message message) {
        // Медиа-контент
        if (message.hasPhoto() || message.hasVoice()) {
            return getHandler(Command.CALORIE_GENERAL);
        }

        if (message.getWebAppData() != null) {
            return getHandler(Command.CALORIE_UTM);
        }

        String text = message.getText();
        if (text == null || text.isEmpty()) {
            return getHandler(Command.CALORIE_GENERAL);
        }

        // Кнопка нижней клавиатуры — подпись на любом языке
        Optional<Command> menuCommand = menuButtons.findCommand(text);
        if (menuCommand.isPresent()) return getHandler(menuCommand.get());

        // Специфичные команды через startsWith
        if (text.startsWith(Command.CALORIE_WATER.getCommandText()))         return getHandler(Command.CALORIE_WATER);
        if (text.startsWith(Command.CALORIE_SETTINGS.getCommandText()))      return getHandler(Command.CALORIE_SETTINGS);
        if (text.startsWith(Command.CALORIE_ADMIN_MESSAGE.getCommandText())) return getHandler(Command.CALORIE_ADMIN_MESSAGE);
        if (text.startsWith(Command.CALORIE_FAVORITE.getCommandText()))      return getHandler(Command.CALORIE_FAVORITE);
        if (text.startsWith(Command.CALORIE_PROFILE.getCommandText())) return getHandler(Command.CALORIE_PROFILE);

        // Попытка найти хендлер по первому слову (команде)
        String commandKey = text.split(TelegramBot.DEFAULT_DELIMETER)[0];
        UpdateHandler handler = getUpdateHandlerMap().get(commandKey);

        return (handler != null) ? handler : getHandler(Command.CALORIE_GENERAL);
    }

    // Хелпер, чтобы не писать каждый раз длинный вызов мапы
    private UpdateHandler getHandler(Command command) {
        return getUpdateHandlerMap().get(command.getCommandText());
    }
}
