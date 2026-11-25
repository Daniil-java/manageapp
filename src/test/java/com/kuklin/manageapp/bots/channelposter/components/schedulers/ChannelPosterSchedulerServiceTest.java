package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import com.kuklin.manageapp.bots.channelposter.services.ChannelAutopilotService;
import com.kuklin.manageapp.bots.channelposter.services.PollResultsService;
import com.kuklin.manageapp.bots.channelposter.services.source.ContentPipeline;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChannelPosterSchedulerServiceTest {

    private final ContentPipeline pipeline = mock(ContentPipeline.class);
    private final ChannelAutopilotService autopilot = mock(ChannelAutopilotService.class);
    private final ChannelPosterSchedulerService scheduler = new ChannelPosterSchedulerService(
            mock(PostPublishChannelScheduleProcessor.class), mock(PathImageCleanerScheduleProcessor.class),
            pipeline, autopilot, mock(PollResultsService.class));

    @Test
    void heavyStepsLeaveSchedulerThreadImmediately() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        when(pipeline.generate()).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 0;
        });

        long begin = System.nanoTime();
        scheduler.generatePosts();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);

        assertThat(elapsedMs).isLessThan(1000);
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        scheduler.shutdownPipeline();
    }

    @Test
    void failedStepDoesNotStopNextOnes() throws Exception {
        CountDownLatch filtered = new CountDownLatch(1);
        when(pipeline.generate()).thenThrow(new IllegalStateException("AI is down"));
        when(pipeline.filter()).thenAnswer(inv -> {
            filtered.countDown();
            return 0;
        });

        scheduler.generatePosts();
        scheduler.filterSourceItems();

        assertThat(filtered.await(2, TimeUnit.SECONDS)).isTrue();
        scheduler.shutdownPipeline();
    }

    @Test
    void fullQueueSkipsStepInsteadOfBlocking() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        Runnable slow = () -> {
            runs.incrementAndGet();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // один выполняется, PIPELINE_QUEUE ждут, остальные пропускаются без ожидания
        for (int i = 0; i < ChannelPosterSchedulerService.PIPELINE_QUEUE + 3; i++) {
            scheduler.runInPipeline("slow", slow);
        }
        release.countDown();
        Thread.sleep(300);

        assertThat(runs.get()).isEqualTo(ChannelPosterSchedulerService.PIPELINE_QUEUE + 1);
        scheduler.shutdownPipeline();
    }

    @Test
    void autopilotFailureStillExpiresStaleReviews() {
        when(autopilot.runAutopilot()).thenThrow(new IllegalStateException("db"));

        scheduler.autopilot();

        verify(autopilot).expireStaleReviews();
    }
}
