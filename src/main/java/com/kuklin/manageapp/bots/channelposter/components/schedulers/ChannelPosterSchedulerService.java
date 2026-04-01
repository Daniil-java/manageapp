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

    @Scheduled(cron = "0 0/10 * * * *")
    private void postPublishChannelScheduler() {
        getInfo(postPublishChannelScheduleProcessor.getSchedulerName());
        postPublishChannelScheduleProcessor.process();
    }

    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
