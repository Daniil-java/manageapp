package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.parser.Subreddit;
import com.kuklin.manageapp.bots.channelposter.services.parser.SubredditService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * @deprecated Команда /sub. Заменена на «/src add reddit». Снят @Component, чтобы /sub не писал в старую таблицу.
 * <p>Устарело в et-85. Причина: old.reddit.com без логина теперь редиректит на /login (reason=lor2) —
 * парсер получал страницу входа вместо ленты и находил 0 постов, а ошибки уходили только в warn-лог.
 * Поэтому в канал почти ничего не приходило. Вместо Reddit-only конвейера сделан общий:
 * ContentSource / SourceItem, парсеры RSS, PubMed и Reddit (официальный API) и ContentPipeline.
 * <p>Код не запускается по расписанию и не пополняет таблицы subreddit / reddit_post.
 * Оставлен для истории; удалить вместе с таблицами отдельной миграцией.
 */
@Deprecated(since = "et-85", forRemoval = true)
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
