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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ArticlePosterUpdateHandler.getGeneratedTextKeyboard;

@Component
@RequiredArgsConstructor
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
            if (post.getContent() == null || post.getContent().isBlank()) continue;

            try {
                PostQueue postQueue = postQueueService.createPostQueueByText(
                        post.getContent(),
                        TopicCategory.TopicType.ARTICLE
                );

                for (Long id: botKeyComponent.getAdminIds()) {
                    channelPosterTelegramBot.sendReturnedMessage(
                            id,
                            postQueue.getTextContent(),
                            getGeneratedTextKeyboard(postQueue.getId()),
                            null
                    );
                }
            } catch (TopicCategoryNotFoundException e) {
                postQueueService.markAsFailed(post.getId());
            }
        }
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
