package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import com.kuklin.manageapp.bots.channelposter.services.ChannelAutopilotService;
import com.kuklin.manageapp.bots.channelposter.services.PollResultsService;
import com.kuklin.manageapp.bots.channelposter.services.source.ContentPipeline;
import com.kuklin.manageapp.common.configurations.BotScheduler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Расписание канала (время московское):
 * сбор источников раз в 6 ч → AI-фильтр каждый час → генерация постов каждые 2 ч →
 * автопилот и снятие залежавшихся на проверке постов каждый час,
 * публикация по слотам каждые 10 мин, итоги квизов и опросов каждый час в :05, отчёт админам в 10:00.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@BotScheduler(BotIdentifier.CHANNEL_POSTER)
public class ChannelPosterSchedulerService {
    private static final String ZONE = "Europe/Moscow";
    // сколько шагов конвейера может ждать, пока идёт текущий; больше — шаг пропускается до следующего запуска
    static final int PIPELINE_QUEUE = 3;

    private final PostPublishChannelScheduleProcessor postPublishChannelScheduleProcessor;
    private final PathImageCleanerScheduleProcessor pathImageCleanerScheduleProcessor;
    private final ContentPipeline contentPipeline;
    private final ChannelAutopilotService autopilotService;
    private final PollResultsService pollResultsService;

    /*
     * Сбор, AI-фильтр и генерация (рубрики с картинками, угадайка, статьи) идут минутами. Пул @Scheduled
     * общий для всех ботов (spring.task.scheduling.pool.size: 4, один поток может часами держать HH) —
     * поэтому тяжёлые шаги канала уходят в свой поток, а поток шедулера освобождается сразу.
     * Шаги и так не пересекаются (замок ContentPipeline), так что одного потока хватает.
     * Публикация, автопилот, итоги и отчёт быстрые и остаются в шедулере: их не задерживает генерация.
     */
    private final ThreadPoolExecutor pipelineExecutor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(PIPELINE_QUEUE),
            runnable -> {
                Thread thread = new Thread(runnable, "channel-pipeline");
                thread.setDaemon(true);
                return thread;
            });

    // 03:00, 09:00, 15:00, 21:00
    @Scheduled(cron = "0 0 3,9,15,21 * * *", zone = ZONE)
    public void fetchSources() {
        getInfo("fetchSources");
        runInPipeline("fetchSources", () -> log.info("Sources fetched: {} new items", contentPipeline.fetch()));
    }

    @Scheduled(cron = "0 20 * * * *", zone = ZONE)
    public void filterSourceItems() {
        getInfo("filterSourceItems");
        runInPipeline("filterSourceItems", contentPipeline::filter);
    }

    // 08:40 … 22:40 — ночью превью админам не шлём
    @Scheduled(cron = "0 40 8-22/2 * * *", zone = ZONE)
    public void generatePosts() {
        getInfo("generatePosts");
        runInPipeline("generatePosts", contentPipeline::generate);
    }

    @Scheduled(cron = "0 50 * * * *", zone = ZONE)
    public void autopilot() {
        getInfo("autopilot");
        // сбой автопилота не должен оставлять залежавшиеся посты на проверке — и наоборот
        try {
            int queued = autopilotService.runAutopilot();
            if (queued > 0) {
                log.info("Autopilot queued {} posts", queued);
            }
        } catch (Exception e) {
            log.error("Autopilot failed", e);
        }
        // после автопилота: сильные посты он уже забрал, снимаем только залежавшиеся остальные
        try {
            autopilotService.expireStaleReviews();
        } catch (Exception e) {
            log.error("Expiring stale reviews failed", e);
        }
    }

    // итоги квизов и опросов — на следующий день в channelposter.poll-results-hour, здесь проверяем каждый час
    @Scheduled(cron = "0 5 * * * *", zone = ZONE)
    public void pollResults() {
        getInfo("pollResults");
        int published = pollResultsService.publishDueResults();
        if (published > 0) {
            log.info("Poll results published: {}", published);
        }
    }

    @Scheduled(cron = "0 0 10 * * *", zone = ZONE)
    public void dailyReport() {
        getInfo("dailyReport");
        autopilotService.sendDailyReport();
    }

    @Scheduled(cron = "0 0/10 * * * *")
    public void postPublishChannelScheduler() {
        getInfo(postPublishChannelScheduleProcessor.getSchedulerName());
        postPublishChannelScheduleProcessor.process();
    }

//    @Scheduled(cron = "0 0 3 * * *")
    private void pathImageCleanerScheduleProcessor() {
        getInfo(pathImageCleanerScheduleProcessor.getSchedulerName());
        pathImageCleanerScheduleProcessor.process();
    }

    /**
     * Шаг конвейера — в поток канала. Очередь полна (генерация идёт дольше часа) — шаг пропускаем:
     * следующий запуск по расписанию сделает то же самое.
     */
    void runInPipeline(String name, Runnable step) {
        try {
            pipelineExecutor.execute(() -> {
                try {
                    step.run();
                } catch (Exception e) {
                    log.error("Channel pipeline step {} failed", name, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Channel pipeline is busy — {} skipped until next run", name);
        }
    }

    @PreDestroy
    void shutdownPipeline() {
        pipelineExecutor.shutdownNow();
    }

    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
