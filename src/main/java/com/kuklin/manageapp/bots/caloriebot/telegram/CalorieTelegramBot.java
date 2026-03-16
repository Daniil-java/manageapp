package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
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

    public CalorieTelegramBot(TelegramCaloriesBotKeyComponents telegramCaloriesBotKeyComponents) {
        super(telegramCaloriesBotKeyComponents.getKey());
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
        boolean result = doAsync(
                asyncService,
                update,
                u -> telegramCalorieBotFacade.handleUpdate(update)
        );

        if (!result) {
            if (update.hasPreCheckoutQuery()
                    || (update.hasMessage() && update.getMessage().hasSuccessfulPayment())) {
                telegramCalorieBotFacade.handleUpdate(update);
            } else {
                notifyAlreadyInProcess(update);
            }
        }
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

