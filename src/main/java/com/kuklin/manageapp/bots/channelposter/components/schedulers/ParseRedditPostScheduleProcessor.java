package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import com.kuklin.manageapp.bots.channelposter.components.PostParser;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.entities.parser.RedditPost;
import com.kuklin.manageapp.bots.channelposter.model.TopicCategoryNotFoundException;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.services.parser.RedditPostService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ArticlePosterUpdateHandler.getGeneratedTextKeyboard;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParseRedditPostScheduleProcessor implements ScheduleProcessor {
    private final RedditPostService redditPostService;
    private final PostParser parser;
    private final PostQueueService postQueueService;
    private final ChannelPosterBotKeyComponent botKeyComponent;
    private final ChannelPosterTelegramBot channelPosterTelegramBot;

    private static final int MAX_COUNT = 10;

    @Override
    public void process() {

        List<RedditPost> redditPosts =
                redditPostService.getByStatus(RedditPost.PostStatus.APPROVED);
        log.info("{} has {} approved posts!", getSchedulerName(), redditPosts.size());

        if (redditPosts == null || redditPosts.isEmpty()) return;

        // берём не больше MAX_COUNT
        List<RedditPost> toProcess = redditPosts.stream()
                .limit(MAX_COUNT)
                .toList();

        List<RedditPost> success = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();

        for (RedditPost post : toProcess) {
            try {
                ThreadUtil.sleep(1500); // чуть мягче

                String content = parser.parseContent(post);

                if (content == null || content.isBlank()) {
                    failedIds.add(post.getId());
                    continue;
                }

                post.setContent(content)
                        .setStatus(RedditPost.PostStatus.PROCESSED);

                success.add(post);

            } catch (Exception e) {
                failedIds.add(post.getId());
            }
        }
        log.info("{} has {} success posts!", getSchedulerName(), success.size());
        log.info("{} has {} failed posts!", getSchedulerName(), failedIds.size());

        // сохраняем успешно обработанные
        if (!success.isEmpty()) {
            redditPostService.saveAll(success);
        }

        // помечаем упавшие
        if (!failedIds.isEmpty()) {
            redditPostService.updateStatuses(failedIds, RedditPost.PostStatus.FAILED);
        }

        generateAiArticle(success);
    }

    private void generateAiArticle(List<RedditPost> posts) {
        for (RedditPost post : posts) {
            try {
                processSinglePost(post);
            } catch (Exception e) {
                log.error("Критическая ошибка при обработке поста {}: {}", post.getId(), e.getMessage());
                postQueueService.markAsFailed(post.getId());
            }
        }
    }

    private void processSinglePost(RedditPost post) {
        if (post.getContent() == null || post.getContent().isBlank()) {
            return;
        }

        String finalContent = post.getContent();

        // 1. Извлекаем контент, если это ссылка
        if (post.getContentType().equals(RedditPost.ContentType.LINK)) {
            String extractedArticle = parser.extractGenericContent(post.getUrl());
            if (extractedArticle == null) {
                log.warn("Не удалось извлечь контент по ссылке: {}", post.getUrl());
                postQueueService.markAsFailed(post.getId());
                return;
            }
            finalContent = extractedArticle;
        }

        // 2. Создаем запись в очереди (уже с финальным текстом)
        PostQueue postQueue;
        try {
            postQueue = postQueueService.createPostQueueByText(
                    finalContent,
                    TopicCategory.TopicType.ARTICLE
            );
        } catch (TopicCategoryNotFoundException e) {
            log.error("Категория ARTICLE не найдена для поста {}", post.getId());
            postQueueService.markAsFailed(post.getId());
            return;
        }

        // 3. Рассылка админам
        notifyAdmins(postQueue);
    }

    private void notifyAdmins(PostQueue postQueue) {
        for (Long adminId : botKeyComponent.getAdminIds()) {
            try {
                channelPosterTelegramBot.sendReturnedMessage(
                        adminId,
                        postQueue.getTextContent(),
                        getGeneratedTextKeyboard(postQueue.getId()),
                        null
                );
            } catch (Exception e) {
                log.error("Ошибка отправки админу {}: {}", adminId, e.getMessage());
                // Если одному админу не ушло, не помечаем всю очередь как PROCESSED сразу,
                // чтобы не стопнуть процесс для остальных.
            }
        }
        // Здесь можно обновить статус очереди, если это необходимо по твоей бизнес-логике
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
