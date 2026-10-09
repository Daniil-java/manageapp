package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.services.PostReviewService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ReviewPosterUpdateHandler.CHANNEL_ZONE;
import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ReviewPosterUpdateHandler.TIME_FORMAT;

/**
 * /q — очередь публикаций и посты на проверке. «/q 12» — заново прислать превью поста #12.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class GetQueuePosterUpdateHandler implements ChannelPosterUpdateHandler {
    private final ChannelPosterTelegramBot channelPosterTelegramBot;
    private final PostQueueService postQueueService;
    private final PostReviewService postReviewService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasMessage()) return;
        Long chatId = update.getMessage().getChatId();
        String[] parts = update.getMessage().getText().trim().split("\\s+");

        if (parts.length > 1) {
            resendPreview(chatId, parts[1]);
            return;
        }

        List<PostQueue> queued = postQueueService.getQueuedOrdered();
        List<PostQueue> review = postQueueService.getPostsByStatus(PostQueue.PostQueueStatus.REVIEW);

        StringBuilder sb = new StringBuilder("<b>Очередь</b> (").append(queued.size()).append(")\n");
        for (PostQueue post : queued) {
            sb.append(post.getScheduledAt().atZone(CHANNEL_ZONE).format(TIME_FORMAT))
                    .append(" · #").append(post.getId()).append(' ').append(escape(post.getTitle())).append('\n');
        }
        if (queued.isEmpty()) {
            sb.append("пусто\n");
        }

        sb.append("\n<b>На проверке</b> (").append(review.size()).append(")\n");
        for (PostQueue post : review) {
            sb.append("#").append(post.getId()).append(' ').append(escape(post.getTitle()));
            if (post.getAiScore() != null) {
                sb.append(" · AI ").append(post.getAiScore()).append("/10");
            }
            sb.append('\n');
        }
        if (!review.isEmpty()) {
            sb.append("\n/q <i>id</i> — прислать превью ещё раз");
        }

        channelPosterTelegramBot.sendReturnedMessage(chatId, sb.toString());
    }

    private void resendPreview(Long chatId, String rawId) {
        try {
            PostQueue post = postQueueService.getPostQueueById(Long.parseLong(rawId));
            postReviewService.sendPreview(chatId, post, true);
        } catch (Exception e) {
            channelPosterTelegramBot.sendReturnedMessage(chatId, "Нет поста #" + escape(rawId));
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_GET_QUEUE.getCommandText();
    }
}
