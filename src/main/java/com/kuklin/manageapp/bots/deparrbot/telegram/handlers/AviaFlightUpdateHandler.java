package com.kuklin.manageapp.bots.deparrbot.telegram.handlers;

import com.kuklin.manageapp.bots.deparrbot.models.FlightCode;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import com.kuklin.manageapp.bots.deparrbot.services.FlightInfoService;
import com.kuklin.manageapp.bots.deparrbot.services.UserFlightService;
import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AviaFlightUpdateHandler implements AviaUpdateHandler{
    private final FlightInfoService flightInfoService;
    private final UserFlightService userFlightService;
    private final AviaTelegramBot aviaTelegramBot;
    private static final String ERROR_FLIGHT_MSG = "Не удалось найти рейс ни на flightstats, ни на flightaware";
    private static final String ERROR_MSG = "Неверный формат. Пример: /flight AK 880 или /flight AK880";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Ожидается команда, типа "/flight AK 880" или "/flight AK880"
        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText().trim();

        int commandEnd = messageText.indexOf(TelegramBot.DEFAULT_DELIMETER);
        FlightCode code = commandEnd < 0 ? null : FlightCode.parseOrNull(messageText.substring(commandEnd + 1));
        if (code == null) {
            aviaTelegramBot.sendReturnedMessage(chatId, ERROR_MSG);
            return;
        }
        String flight = code.carrier();
        String number = code.number();

        FlightDto flightDto = flightInfoService.findFlightOrNull(code);
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
