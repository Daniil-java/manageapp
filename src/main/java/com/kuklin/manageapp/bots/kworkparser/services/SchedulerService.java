package com.kuklin.manageapp.bots.kworkparser.services;

import com.kuklin.manageapp.bots.kworkparser.processors.KworkScheduleProcessor;
import com.kuklin.manageapp.bots.kworkparser.processors.NotificationScheduleProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {
    private final KworkScheduleProcessor kworkScheduleProcessor;
    private final NotificationScheduleProcessor notificationScheduleProcessor;

    @Scheduled(cron = "0 */10 * * * *")
    public void kworkScheduleProcessor() {
        log.info(kworkScheduleProcessor.getSchedulerName() + " started working!");
        kworkScheduleProcessor.process();
    }

    @Scheduled(cron = "0 */1 * * * *")
    public void notificationScheduleProcessor() {
        log.info(notificationScheduleProcessor.getSchedulerName() + " started working!");
        notificationScheduleProcessor.process();
    }


}
