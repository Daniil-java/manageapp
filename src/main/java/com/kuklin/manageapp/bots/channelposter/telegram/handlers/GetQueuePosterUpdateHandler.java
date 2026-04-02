package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.PostMessagePosterUpdateHandler.defChannelZoneId;

@RequiredArgsConstructor
@Component
@Slf4j
public class GetQueuePosterUpdateHandler implements ChannelPosterUpdateHandler{
    private final ChannelPosterTelegramBot channelPosterTelegramBot;
    private final PostQueueService postQueueService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        List<PostQueue> postQueues = postQueueService.getPostsByStatus(PostQueue.PostQueueStatus.QUEUED);

        StringBuilder sb = new StringBuilder();
        for (PostQueue post: postQueues) {
            Instant time = post.getScheduledAt();
            ZonedDateTime moscowTime = time.atZone(defChannelZoneId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

            sb.append(post.getTextContent().substring(0, 50))
                    .append("\nПост будет опубликован: " + moscowTime.format(formatter))
                    .append("\n");
        }

        channelPosterTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                sb.toString()
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.POSTER_GET_QUEUE.getCommandText();
    }
}
