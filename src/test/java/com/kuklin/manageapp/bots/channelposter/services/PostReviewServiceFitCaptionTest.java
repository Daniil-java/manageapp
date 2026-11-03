package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.ArticleContentGenerator;
import com.kuklin.manageapp.bots.channelposter.components.PostPublisher;
import com.kuklin.manageapp.bots.channelposter.components.source.PixabayClient;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Пост не влезает в подпись к фото — перед отправкой на проверку его один раз сокращают под неё.
 */
class PostReviewServiceFitCaptionTest {

    private static final long ADMIN = 1L;
    private static final long POST_ID = 5L;

    private final ChannelPosterTelegramBot bot = mock(ChannelPosterTelegramBot.class);
    private final ChannelPosterBotKeyComponent keys = mock(ChannelPosterBotKeyComponent.class);
    private final PostQueueService postQueueService = mock(PostQueueService.class);
    private final PostPublisher publisher = mock(PostPublisher.class);
    private final PostReviewService service = new PostReviewService(
            bot, keys, mock(ArticleContentGenerator.class), mock(PostImageService.class), postQueueService, publisher,
            new ChannelPosterProperties(), mock(TopicCategoryService.class), mock(ChannelPollService.class),
            mock(PixabayClient.class));

    @BeforeEach
    void setUp() throws Exception {
        when(postQueueService.markReview(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postQueueService.makePostQueueContentFitCaption(anyLong(), anyInt()))
                .thenAnswer(inv -> post(700));
    }

    @Test
    void postOverCaptionIsShortenedOnceWithMargin() throws Exception {
        PostQueue post = post(1100);
        when(publisher.getCaptionOverflow(post)).thenReturn(120);

        service.prepareAndSend(post, List.of(ADMIN));

        // 1100 − 120 лишних − 100 запаса
        verify(postQueueService).makePostQueueContentFitCaption(POST_ID, 880);
        verify(postQueueService).markReview(argThat(p -> p.getTextContent().length() == 700));
    }

    @Test
    void postThatFitsIsNotTouched() throws Exception {
        PostQueue post = post(800);
        when(publisher.getCaptionOverflow(post)).thenReturn(0);

        service.prepareAndSend(post, List.of(ADMIN));

        verify(postQueueService, never()).makePostQueueContentFitCaption(anyLong(), anyInt());
        verify(postQueueService).markReview(post);
    }

    @Test
    void shorteningNeverAsksForLessThanMinimum() throws Exception {
        PostQueue post = post(500);
        when(publisher.getCaptionOverflow(post)).thenReturn(300);

        service.prepareAndSend(post, List.of(ADMIN));

        verify(postQueueService).makePostQueueContentFitCaption(POST_ID, PostReviewService.MIN_FIT_CHARS);
    }

    private static PostQueue post(int chars) {
        PostQueue post = new PostQueue().setTextContent("а".repeat(chars));
        post.setId(POST_ID);
        return post;
    }
}
