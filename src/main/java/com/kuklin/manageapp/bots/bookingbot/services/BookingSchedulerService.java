package com.kuklin.manageapp.bots.bookingbot.services;

import com.kuklin.manageapp.common.configurations.BotScheduler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.bots.bookingbot.processors.BookingStatusScheduleProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@BotScheduler(BotIdentifier.BOOKING_BOT)
public class BookingSchedulerService {
    private final BookingStatusScheduleProcessor bookingStatusScheduleProcessor;

    @Scheduled(cron = "0 */10 * * * *")
    public void ordersCheckUpdateScheduleProcessor() {
        log.info(bookingStatusScheduleProcessor.getSchedulerName() + " started working!");
        bookingStatusScheduleProcessor.process();
    }


}
