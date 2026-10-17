package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelPosterSchedulerService {
    private final PostPublishChannelScheduleProcessor postPublishChannelScheduleProcessor;
    private final PathImageCleanerScheduleProcessor pathImageCleanerScheduleProcessor;
    private final ParseRedditPostScheduleProcessor parseRedditPostScheduleProcessor;
    private final RedditPostFilterPosterScheduler redditPostFilterPosterScheduler;
    private final SubredditPosterScheduler subredditPosterScheduler;

    // 00:00, 06:00, 12:00, 18:00
    @Scheduled(cron = "0 0 0,6,12,18 * * *")
    private void subredditPosterScheduler() {
        getInfo(subredditPosterScheduler.getSchedulerName());
        subredditPosterScheduler.process();
    }

    // 00:20, 06:20, 12:20, 18:20
    @Scheduled(cron = "0 20 0,6,12,18 * * *")
    private void redditPostFilterPosterScheduler() {
        getInfo(redditPostFilterPosterScheduler.getSchedulerName());
        redditPostFilterPosterScheduler.process();
    }

    // 00:40, 06:40, 12:40, 18:40
    @Scheduled(cron = "0 40 0,6,12,18 * * *")
    private void parseRedditPostScheduleProcessor() {
        getInfo(parseRedditPostScheduleProcessor.getSchedulerName());
        parseRedditPostScheduleProcessor.process();
    }

    @Scheduled(cron = "0 0/10 * * * *")
    private void postPublishChannelScheduler() {
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
