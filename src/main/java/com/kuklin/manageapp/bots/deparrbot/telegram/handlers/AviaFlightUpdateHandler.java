package com.kuklin.manageapp.bots.deparrbot.telegram.handlers;

import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import com.kuklin.manageapp.bots.deparrbot.providers.FlightInfoProvider;
import com.kuklin.manageapp.bots.deparrbot.services.UserFlightService;
import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AviaFlightUpdateHandler implements AviaUpdateHandler{
    private final FlightInfoProvider flightInfoProvider;
    private final UserFlightService userFlightService;
    private final AviaTelegramBot aviaTelegramBot;
    private static final String ERROR_FLIGHT_MSG = "Не удалось получить информацию";
    private static final String ERROR_MSG = "Не верный формат сообщения!";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Ожидается команда, типа "/start SU 123"
        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();

        String[] flightNumber = messageText.trim().split(TelegramBot.DEFAULT_DELIMETER);
        if (flightNumber.length < 3) {
            aviaTelegramBot.sendReturnedMessage(chatId, ERROR_FLIGHT_MSG);
            return;
        }
        String flight = flightNumber[1].toUpperCase();
        String number = flightNumber[2].toUpperCase();

        FlightDto flightDto = flightInfoProvider.getFlightInfoOrNull(flight, number);
        if (flightDto == null) {
            aviaTelegramBot.sendReturnedMessage(chatId, ERROR_FLIGHT_MSG);
            return;
        }

        Boolean isSub = userFlightService
                .isUserSubscribe(flight, number, telegramUser.getTelegramId());

        InlineKeyboardMarkup markup;
        if (isSub) {
            markup = getInlineMessageUnSubscribeFlight(flight, number);
        } else {
            markup = getInlineMessageSubscribeFlight(flight, number);
        }

        aviaTelegramBot.sendReturnedMessage(
                chatId, flightDto.getFlightInfoText(), markup, null
        );

    }

    public static InlineKeyboardMarkup getInlineMessageFlight(String flight, String num, Command command, String buttonText) {
        String callbackData = String.join(TelegramBot.DEFAULT_DELIMETER,
                command.getCommandText(), flight, num);

        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setCallbackData(callbackData);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(Collections.singletonList(button)));

        return markup;
    }

    public static InlineKeyboardMarkup getInlineMessageSubscribeFlight(String flight, String num) {
        return getInlineMessageFlight(
                flight, num,
                Command.AVIA_SUBSCRIBE, Command.AVIA_SUBSCRIBE.getCommandText());
    }

    public static InlineKeyboardMarkup getInlineMessageUnSubscribeFlight(String flight, String num) {
        return getInlineMessageFlight(
                flight, num,
                Command.AVIA_UNSUBSCRIBE, Command.AVIA_UNSUBSCRIBE.getCommandText());
    }

    @Override
    public String getHandlerListName() {
        return Command.AVIA_FLIGHT.getCommandText();
    }
}
