package com.kuklin.manageapp.bots.bookingbot.telegram;

import com.kuklin.manageapp.bots.bookingbot.configurations.TelegramBookingBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class BookingTelegramBot extends TelegramBot {
    public static final BotIdentifier BOT_IDENTIFIER = BotIdentifier.BOOKING_BOT;
    @Autowired
    private BookingTelegramFacade bookingTelegramFacade;
    @Autowired
    private AsyncService asyncService;

    public static final String BOOKING_DELIMETER = "&";

    public BookingTelegramBot(TelegramBookingBotKeyComponents components) {
        super(components.getKey());
    }

    @Override
    public void onUpdateReceived(Update update) {
        boolean result = doAsync(asyncService, update, u -> bookingTelegramFacade.handleUpdate(update));

        if (!result) {
            notifyAlreadyInProcess(update);
        }
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BOT_IDENTIFIER;
    }

    @Override
    public String getBotUsername() {
        return BOT_IDENTIFIER.getBotUsername();
    }
}
