package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import com.kuklin.manageapp.bots.channelposter.services.ChannelAutopilotService;
import com.kuklin.manageapp.bots.channelposter.services.source.ContentPipeline;
import com.kuklin.manageapp.common.configurations.BotScheduler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Расписание канала (время московское):
 * сбор источников раз в 6 ч → AI-фильтр каждый час → генерация постов каждые 2 ч →
 * автопилот и снятие залежавшихся на проверке постов каждый час,
 * публикация по слотам каждые 10 мин, отчёт админам в 10:00.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@BotScheduler(BotIdentifier.CHANNEL_POSTER)
public class ChannelPosterSchedulerService {
    private static final String ZONE = "Europe/Moscow";

    private final PostPublishChannelScheduleProcessor postPublishChannelScheduleProcessor;
    private final PathImageCleanerScheduleProcessor pathImageCleanerScheduleProcessor;
    private final ContentPipeline contentPipeline;
    private final ChannelAutopilotService autopilotService;

    // 03:00, 09:00, 15:00, 21:00
    @Scheduled(cron = "0 0 3,9,15,21 * * *", zone = ZONE)
    public void fetchSources() {
        getInfo("fetchSources");
        log.info("Sources fetched: {} new items", contentPipeline.fetch());
    }

    @Scheduled(cron = "0 20 * * * *", zone = ZONE)
    public void filterSourceItems() {
        getInfo("filterSourceItems");
        contentPipeline.filter();
    }

    // 08:40 … 22:40 — ночью превью админам не шлём
    @Scheduled(cron = "0 40 8-22/2 * * *", zone = ZONE)
    public void generatePosts() {
        getInfo("generatePosts");
        contentPipeline.generate();
    }

    @Scheduled(cron = "0 50 * * * *", zone = ZONE)
    public void autopilot() {
        getInfo("autopilot");
        int queued = autopilotService.runAutopilot();
        if (queued > 0) {
            log.info("Autopilot queued {} posts", queued);
        }
        // после автопилота: сильные посты он уже забрал, снимаем только залежавшиеся остальные
        autopilotService.expireStaleReviews();
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

    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
