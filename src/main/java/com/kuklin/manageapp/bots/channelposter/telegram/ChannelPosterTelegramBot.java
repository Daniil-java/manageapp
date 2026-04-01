package com.kuklin.manageapp.bots.channelposter.telegram;

import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
public class ChannelPosterTelegramBot extends TelegramBot {

    private final List<Long> adminsIds;

    @Autowired
    private ChannelPosterTelegramFacade channelPosterTelegramFacade;

    public ChannelPosterTelegramBot(ChannelPosterBotKeyComponent components) {
        super(components.getKey());
        adminsIds = components.getAdminIds();
    }

    @Override
    public void handleUpdateDirectly(Update update) {
        channelPosterTelegramFacade.handleUpdate(update);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!(update.hasMessage() || update.hasCallbackQuery())) {
            return;
        }
        Long userId = update.hasMessage() ?
                update.getMessage().getFrom().getId() :
                update.getCallbackQuery().getFrom().getId();
        if (!adminsIds.contains(userId)) return;
        channelPosterTelegramFacade.handleUpdate(update);
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.CHANNEL_POSTER;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.CHANNEL_POSTER.getBotUsername();
    }
}
