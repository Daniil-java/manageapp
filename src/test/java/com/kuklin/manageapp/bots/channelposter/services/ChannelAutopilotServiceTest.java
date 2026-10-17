package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.services.source.ContentSourceService;
import com.kuklin.manageapp.bots.channelposter.services.source.SourceItemService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChannelAutopilotServiceTest {

    private final ChannelPosterBotKeyComponent keys = mock(ChannelPosterBotKeyComponent.class);
    private final ChannelPosterTelegramBot bot = mock(ChannelPosterTelegramBot.class);
    private final PostQueueService postQueueService = mock(PostQueueService.class);
    private final ChannelPosterProperties properties = new ChannelPosterProperties();
    private final PostReviewService reviewService = new PostReviewService(
            bot, keys, null, null, postQueueService, null, properties);
    private final ChannelAutopilotService service = new ChannelAutopilotService(
            keys, bot, postQueueService, mock(SourceItemService.class), mock(ContentSourceService.class),
            reviewService, properties);

    @BeforeEach
    void setUp() {
        properties.setAutopilotMinScore(8);
        properties.setAutopilotAfter(Duration.ofHours(12));
        properties.setReviewTtl(Duration.ofDays(3));
        when(keys.getAdminIds()).thenReturn(List.of(1L));
    }

    @Test
    void autopilotQueuesOnlyStrongPostsWaitingLongerThanAutopilotAfter() {
        PostQueue strong = post(1L, 9);
        PostQueue weak = post(2L, 7);
        PostQueue manual = post(3L, null);
        when(postQueueService.getReviewSentBefore(any())).thenReturn(List.of(strong, weak, manual));
        when(postQueueService.assignNextAvailableSlot(eq(strong), any()))
                .thenReturn(strong.setStatus(PostQueue.PostQueueStatus.QUEUED).setScheduledAt(Instant.now()));

        assertThat(service.runAutopilot()).isEqualTo(1);

        verify(postQueueService).getReviewSentBefore(argThat(before ->
                isAbout(before, Instant.now().minus(Duration.ofHours(12)))));
        verify(postQueueService).assignNextAvailableSlot(eq(strong), any());
        verify(postQueueService, never()).assignNextAvailableSlot(eq(weak), any());
        verify(postQueueService, never()).assignNextAvailableSlot(eq(manual), any());
    }

    @Test
    void zeroAutopilotAfterTurnsItOff() {
        properties.setAutopilotAfter(Duration.ZERO);

        assertThat(service.runAutopilot()).isZero();
        assertThat(reviewService.isAutopilotCandidate(post(1L, 10))).isFalse();
        verifyNoInteractions(postQueueService);
    }

    @Test
    void expiresPostsInReviewLongerThanTtlAndTellsAdmins() {
        PostQueue stale1 = post(5L, 7);
        PostQueue stale2 = post(6L, null);
        when(postQueueService.getReviewSentBefore(any())).thenReturn(List.of(stale1, stale2));

        assertThat(service.expireStaleReviews()).isEqualTo(2);

        verify(postQueueService).getReviewSentBefore(argThat(before ->
                isAbout(before, Instant.now().minus(Duration.ofDays(3)))));
        verify(postQueueService).markExpired(stale1);
        verify(postQueueService).markExpired(stale2);
        verify(bot).sendReturnedMessage(eq(1L), contains("#5"));
    }

    @Test
    void nothingStaleNothingSent() {
        when(postQueueService.getReviewSentBefore(any())).thenReturn(List.of());

        assertThat(service.expireStaleReviews()).isZero();
        verifyNoInteractions(bot);
    }

    @Test
    void humanizesDurations() {
        assertThat(PostReviewService.humanize(Duration.ofHours(12))).isEqualTo("12 ч");
        assertThat(PostReviewService.humanize(Duration.ofDays(3))).isEqualTo("3 дн.");
        assertThat(PostReviewService.humanize(Duration.ofMinutes(90))).isEqualTo("90 мин");
    }

    private static PostQueue post(Long id, Integer score) {
        return new PostQueue().setId(id).setTitle("Пост " + id).setAiScore(score)
                .setStatus(PostQueue.PostQueueStatus.REVIEW)
                .setReviewSentAt(Instant.now().minus(Duration.ofDays(4)));
    }

    private static boolean isAbout(Instant actual, Instant expected) {
        return Math.abs(ChronoUnit.SECONDS.between(actual, expected)) < 5;
    }
}
