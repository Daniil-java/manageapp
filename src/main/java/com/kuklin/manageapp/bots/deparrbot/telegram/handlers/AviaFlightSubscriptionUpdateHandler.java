package com.kuklin.manageapp.bots.deparrbot.telegram.handlers;

import com.kuklin.manageapp.bots.deparrbot.services.UserFlightService;
import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@RequiredArgsConstructor
@Component
public class AviaFlightSubscriptionUpdateHandler implements AviaUpdateHandler {
    private final UserFlightService userFlightService;
    private final AviaTelegramBot aviaTelegramBot;
    private static final String SUBSCRIBE_MSG = "Теперь вы будете получать обновление о рейсе: %s";
    private static final String UNSUBSCRIBE_MSG = "Теперь вы не будете получать обновление о рейсе: %s";
    private static final String ERROR_MSG = "Не получилось обновить статус подписки";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String[] messageText = update.getCallbackQuery().getData().trim().split(TelegramBot.DEFAULT_DELIMETER);
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        String command = messageText[0];
        String flightCode = messageText[1].toUpperCase();
        String number = messageText[2].toUpperCase();

        String response = ERROR_MSG;
        InlineKeyboardMarkup inlineKeyboardMarkup = null;

        if (command.equals(Command.AVIA_SUBSCRIBE.getCommandText())) {
            userFlightService.subscribeUserToFlight(telegramUser.getTelegramId(), flightCode, number);
            response = SUBSCRIBE_MSG;
            inlineKeyboardMarkup = AviaFlightUpdateHandler.getInlineMessageUnSubscribeFlight(flightCode, number);

        } else if (command.equals(Command.AVIA_UNSUBSCRIBE.getCommandText())) {
            userFlightService.unsubscribeUserToFlight(telegramUser.getTelegramId(), flightCode, number);
            response = UNSUBSCRIBE_MSG;
            inlineKeyboardMarkup = AviaFlightUpdateHandler.getInlineMessageSubscribeFlight(flightCode, number);
        }

        aviaTelegramBot.sendReturnedMessage(chatId, String.format(response, flightCode + number));
        aviaTelegramBot.editMarkup(
                chatId,
                messageId,
                inlineKeyboardMarkup
        );

    }

    @Override
    public String getHandlerListName() {
        return Command.AVIA_SUBSCRIBE.getCommandText();
    }
}
