package com.kuklin.manageapp.payment.services.scheduler;

import com.kuklin.manageapp.payment.services.scheduler.processors.StarRefundScheduleProcessor;
import com.kuklin.manageapp.payment.services.scheduler.processors.SubscriptionScheduleProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionSchedulerService {
    private final SubscriptionScheduleProcessor subscriptionScheduleProcessor;
    private final StarRefundScheduleProcessor starRefundScheduleProcessor;

    @Scheduled(cron = "0 0/1 * * * *")
    private void subscriptionScheduleProcessor() {
        getInfo(subscriptionScheduleProcessor.getSchedulerName());
        subscriptionScheduleProcessor.process();
    }

    @Scheduled(cron = "0 0 4 * * *")
    private void starRefundScheduleProcessor() {
        getInfo(starRefundScheduleProcessor.getSchedulerName());
        starRefundScheduleProcessor.process();
    }

    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
