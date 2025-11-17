package com.kuklin.manageapp.bots.deparrbot.services;

import com.kuklin.manageapp.bots.deparrbot.processors.FlightSubNotificationScheduleProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AviaSchedulerService {
    private final FlightSubNotificationScheduleProcessor flightSubNotificationScheduleProcessor;

    @Scheduled(cron = "0 */10 * * * *")
    public void ordersCheckUpdateScheduleProcessor() {
        log.info(flightSubNotificationScheduleProcessor.getSchedulerName() + " started working!");
        flightSubNotificationScheduleProcessor.process();
    }


}
