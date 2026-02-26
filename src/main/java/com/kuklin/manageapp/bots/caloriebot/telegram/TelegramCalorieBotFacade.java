package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgmodels.TelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

@Component
@Slf4j
public class TelegramCalorieBotFacade extends TelegramFacade {
    @Autowired
    private TelegramUserService telegramUserService;

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

        UpdateHandler updateHandler = processInputUpdate(update);
        if (updateHandler == null) {
            log.error("Не удалось найти подходящий хендлер. Ответа не будет");
        } else {
            updateHandler.handle(update, telegramUser);
        }
    }

    //TODO метод стал слишком хардкодным. Необходим рефакторинг
    public UpdateHandler processInputUpdate(Update update) {
        // 1. Платежи и чеки (самый высокий приоритет)
        if (update.hasPreCheckoutQuery()) {
            return getHandler(Command.PAYMENT_PRE_CHECK_QUERY);
        }

        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            return getHandler(Command.PAYMENT_SUCCESS);
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

        // Если команда не найдена — скорее всего, это удаление (судя по вашей логике)
        return (handler != null) ? handler : getHandler(Command.CALORIE_DELETE);
    }

    private UpdateHandler handleMessage(Message message) {
        // Медиа-контент
        if (message.hasPhoto() || message.hasVoice()) {
            return getHandler(Command.CALORIE_GENERAL);
        }

        String text = message.getText();
        if (text == null || text.isEmpty()) {
            return getHandler(Command.CALORIE_GENERAL);
        }

        // Специфичные команды через startsWith
        if (text.startsWith(Command.CALORIE_WATER.getCommandText()))         return getHandler(Command.CALORIE_WATER);
        if (text.startsWith(Command.CALORIE_SETTINGS.getCommandText()))      return getHandler(Command.CALORIE_SETTINGS);
        if (text.startsWith(Command.CALORIE_ADMIN_MESSAGE.getCommandText())) return getHandler(Command.CALORIE_ADMIN_MESSAGE);
        if (text.startsWith(Command.CALORIE_FAVORITE.getCommandText()))      return getHandler(Command.CALORIE_FAVORITE);

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
