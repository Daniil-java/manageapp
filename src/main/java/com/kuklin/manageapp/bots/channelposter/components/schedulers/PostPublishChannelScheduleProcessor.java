package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.telegram.handlers.PostMessagePosterUpdateHandler;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PostPublishChannelScheduleProcessor implements ScheduleProcessor {

    private final PostQueueService postQueueService;
    private final PostMessagePosterUpdateHandler posterUpdateHandler;

    @Override
    public void process() {
        // Запрос: SELECT * WHERE status = 'QUEUED' AND scheduled_at <= NOW()
        List<PostQueue> readyToPublish = postQueueService.getPostsReadyToPublish();

        for (PostQueue post : readyToPublish) {
            try {
                log.info("Publishing a post ID: {}", post.getId());

                // Используем метод отправки (нужно вынести ID канала в конфиг)
                Integer tgMessageId = posterUpdateHandler.sendApproveMessage(-1003745287241L, post.getId());

                // Маркируем как отправленный
                postQueueService.markAsSentAndDeleteFile(post.getId(), tgMessageId);
            } catch (Exception e) {
                log.error("Error while posting message ID: {}", post.getId(), e);
                postQueueService.markAsFailed(post.getId());
            }
        }
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
