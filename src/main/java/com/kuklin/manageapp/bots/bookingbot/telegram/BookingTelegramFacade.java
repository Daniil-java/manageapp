package com.kuklin.manageapp.bots.bookingbot.telegram;

import com.kuklin.manageapp.bots.bookingbot.entities.ConversationState;
import com.kuklin.manageapp.bots.bookingbot.services.ConversationStateService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

@Component
public class BookingTelegramFacade extends TelegramFacade {
    @Autowired
    private TelegramUserService telegramUserService;
    @Autowired
    private ConversationStateService conversationStateService;
    @Override
    public void handleUpdate(Update update) {
        if (!update.hasCallbackQuery() && !update.hasMessage()) return;
        User user = update.getMessage() != null ?
                update.getMessage().getFrom() :
                update.getCallbackQuery().getFrom();

        TelegramUser telegramUser = telegramUserService.createOrGetUserByTelegram(BotIdentifier.BOOKING_BOT, user);

        processInputUpdate(update, telegramUser.getTelegramId()).handle(update, telegramUser);
    }

    public UpdateHandler processInputUpdate(Update update, Long telegramUserId) {
        String request;
        if (update.hasCallbackQuery()) {
            request = update.getCallbackQuery().getData().split(BookingTelegramBot.BOOKING_DELIMETER)[0];
        } else {
            request = update.getMessage().getText().split(BookingTelegramBot.BOOKING_DELIMETER)[0];
        }

        UpdateHandler updateHandler = getUpdateHandlerMap().get(request);
        if (updateHandler == null) {
            ConversationState state = getUserState(telegramUserId);
            if (state != null && state.getStep().equals(ConversationState.Step.FILL_FORM)) {
                return getUpdateHandlerMap().get(Command.BOOKING_FORM.getCommandText());
            } else {
                return getUpdateHandlerMap().get(Command.BOOKING_NULL.getCommandText());
            }
        } else {
            return updateHandler;
        }
    }

    private ConversationState getUserState(Long telegramUserId) {
        return conversationStateService
                .getConversationStateByTelegramUserIdOrNull(telegramUserId)
                ;
    }
}
