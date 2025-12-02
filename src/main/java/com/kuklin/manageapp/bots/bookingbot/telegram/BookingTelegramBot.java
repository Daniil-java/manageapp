package com.kuklin.manageapp.bots.bookingbot.telegram;

import com.kuklin.manageapp.bots.bookingbot.configurations.TelegramBookingBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class BookingTelegramBot extends TelegramBot {
    @Autowired
    private BookingTelegramFacade bookingTelegramFacade;

    public static final String BOOKING_DELIMETER = "&";

    public BookingTelegramBot(TelegramBookingBotKeyComponents components) {
        super(components.getKey());
    }

    @Override
    public void onUpdateReceived(Update update) {
        bookingTelegramFacade.handleUpdate(update);
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.BOOKING_BOT;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.BOOKING_BOT.getBotUsername();
    }
}
