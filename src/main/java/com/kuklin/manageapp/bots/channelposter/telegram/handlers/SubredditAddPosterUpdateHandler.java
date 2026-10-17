package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.parser.Subreddit;
import com.kuklin.manageapp.bots.channelposter.services.parser.SubredditService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class SubredditAddPosterUpdateHandler implements ChannelPosterUpdateHandler {
    private final ChannelPosterTelegramBot channelPosterTelegramBot;
    private final SubredditService subredditService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasMessage()) return;
        Subreddit subreddit = subredditService
                .addOrNull(update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER)[1]);
        if (subreddit == null) {
            channelPosterTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    "Не получилось"
            );
        } else {
            channelPosterTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    "Добавлено"
            );
        }

    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_SUBREDDIT.getCommandText();
    }
}
