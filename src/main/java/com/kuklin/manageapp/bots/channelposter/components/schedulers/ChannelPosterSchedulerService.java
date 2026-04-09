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

    // 00:00, 08:00, 16:00
    @Scheduled(cron = "0 0 0,8,16 * * *")
    private void subredditPosterScheduler() {
        getInfo(subredditPosterScheduler.getSchedulerName());
        subredditPosterScheduler.process();
    }

    // 00:20, 08:20, 16:20
    @Scheduled(cron = "0 20 0,8,16 * * *")
    private void redditPostFilterPosterScheduler() {
        getInfo(redditPostFilterPosterScheduler.getSchedulerName());
        redditPostFilterPosterScheduler.process();
    }

    // 00:40, 08:40, 16:40
    @Scheduled(cron = "0 40 0,8,16 * * *")
    private void parseRedditPostScheduleProcessor() {
        getInfo(parseRedditPostScheduleProcessor.getSchedulerName());
        parseRedditPostScheduleProcessor.process();
    }

    @Scheduled(cron = "0 0/10 * * * *")
    private void postPublishChannelScheduler() {
        getInfo(postPublishChannelScheduleProcessor.getSchedulerName());
        postPublishChannelScheduleProcessor.process();
    }

    @Scheduled(cron = "0 0 3 * * *")
    private void pathImageCleanerScheduleProcessor() {
        getInfo(pathImageCleanerScheduleProcessor.getSchedulerName());
        pathImageCleanerScheduleProcessor.process();
    }

    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
