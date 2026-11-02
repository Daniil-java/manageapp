package com.kuklin.manageapp.bots.caloriebot.bot;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.support.SupportGroupService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.subscription.telegram.PaymentPlanListCalorieUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@Slf4j
public class CalorieTelegramBot extends TelegramBot {
    public static final BotIdentifier BOT_IDENTIFIER = BotIdentifier.CALORIE_BOT;
    public static final String DELIMITER = "#cal#";
    @Autowired
    private TelegramCalorieBotFacade telegramCalorieBotFacade;
    @Autowired
    private AsyncService asyncService;
    @Autowired
    @Lazy
    private PaymentPlanListCalorieUpdateHandler paymentPlanListCalorieUpdateHandler;
    @Autowired
    @Lazy
    private TelegramUserService telegramUserService;
    @Autowired
    @Lazy
    private CalorieTexts texts;
    @Autowired
    @Lazy
    private SupportGroupService supportGroupService;

    public CalorieTelegramBot(TelegramCaloriesBotKeyComponents telegramCaloriesBotKeyComponents) {
        super(telegramCaloriesBotKeyComponents.getKey());
    }

    @Override
    protected String getAlreadyInProcessText(Update update) {
        return texts.get(getLanguage(update), BotTextKeys.COMMON_ALREADY_IN_PROCESS);
    }

    @Override
    protected String getOverloadedText(Update update) {
        return texts.get(getLanguage(update), BotTextKeys.COMMON_OVERLOADED);
    }

    /** Язык автора апдейта — до обработчиков, где пользователь ещё не загружен. */
    private AppLanguage getLanguage(Update update) {
        User user = getUserFromUpdate(update);
        TelegramUser telegramUser = user == null ? null
                : telegramUserService.getTelegramUserByTelegramIdAndBotIdentifierOrNull(user.getId(), BOT_IDENTIFIER);
        return texts.languageOf(telegramUser == null ? null : telegramUser.getAppUserId());
    }

    @Override
    public void sendSubExpiredMessage(Long chatId, String text) {
        sendReturnedMessage(chatId, text);
        paymentPlanListCalorieUpdateHandler.sendSubExpiredAdMessage (chatId);
    }

    private void answerCallbackQuery(CallbackQuery callbackQuery) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .build();
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("Send returned message error!", e);
        }
    }

    @Override
    public void handleUpdateDirectly(Update update) {
        telegramCalorieBotFacade.handleUpdate(update);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMyChatMember()) {
            log.info("Received my_chat_member status (bot may be blocked). Chat ID: {}",
                    update.getMyChatMember().getChat().getId());
            return;
        }

        if (update.hasCallbackQuery()) {
            answerCallbackQuery(update.getCallbackQuery());
        }
        // Группа поддержки: сразу, по порядку и без замка «предыдущее сообщение обрабатывается» —
        // иначе второе быстрое сообщение поддержки потерялось бы, а в группу ушло бы «подождите»
        if (supportGroupService.isSupportGroupUpdate(update)) {
            try {
                supportGroupService.handleGroupUpdate(update);
            } catch (RuntimeException e) {
                log.error("Support group update failed", e);
            }
            return;
        }
        // Бот работает только в личке. Другие группы (или группа поддержки с неверным chat-id) — молчим,
        // иначе сообщение в группе распознавалось бы как еда, а бот отвечал бы туда с клавиатурой меню
        if (isGroupUpdate(update)) {
            log.warn("Update from group chat {} ignored", findChatIdOrNull(update));
            return;
        }
        boolean result = doAsync(
                asyncService,
                update,
                u -> telegramCalorieBotFacade.handleUpdate(update)
        );

        if (!result) {
            if (isPaymentUpdate(update)) {
                telegramCalorieBotFacade.handleUpdate(update);
            } else {
                notifyAlreadyInProcess(update);
            }
        }
    }

    /** Сообщение или кнопка не из лички: у групп и каналов id чата отрицательный, у лички — id пользователя. */
    static boolean isGroupUpdate(Update update) {
        Long chatId = findChatIdOrNull(update);
        return chatId != null && chatId < 0;
    }

    // Pre-checkout чата не имеет — null
    private static Long findChatIdOrNull(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }

    @Override
    public String getBotUsername() {
        return BOT_IDENTIFIER.getBotUsername();
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BOT_IDENTIFIER;
    }
}

