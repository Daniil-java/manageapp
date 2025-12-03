package com.kuklin.manageapp.bots.bookingbot.processors;

import com.kuklin.manageapp.bots.bookingbot.entities.Booking;
import com.kuklin.manageapp.bots.bookingbot.services.BookingService;
import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingStatusScheduleProcessor implements ScheduleProcessor {
    private final BookingService bookingService;
    private final TelegramUserService telegramUserService;
    private final BookingTelegramBot bookingTelegramBot;
    private static final String BOOKING_REMOVE = "Время бронирование истекло!";
    @Override
    @Transactional
    public void process() {
        //Забронированные
        List<Booking> bookingsBooked = bookingService.getAllBookingsByStatus(Booking.Status.BOOKED);
        //В процессе бронирования
        List<Booking> bookingsBooking = bookingService.getAllBookingsByStatus(Booking.Status.BOOKING);
        checkAndProcessBooked(bookingsBooked);
        checkAndProcessBooking(bookingsBooking);
    }

    private void checkAndProcessBooked(List<Booking> bookings) {
        LocalDateTime now = LocalDateTime.now();
        for (Booking booking : bookings) {
            if (booking.getEndTime().isBefore(now)) {
                bookingService.updateStatus(booking, Booking.Status.COMPLETED);
            }
        }
    }

    private void checkAndProcessBooking(List<Booking> bookings) {
        int defaultBookingProcessTime = 10;
        LocalDateTime now = LocalDateTime.now();
        for (Booking booking : bookings) {
            if (Duration.between(booking.getUpdatedAt(), now).toMinutes() > defaultBookingProcessTime) {
                bookingService.deleteById(booking.getId());
                TelegramUser telegramUser = telegramUserService.getTelegramUserByIdOrNull(booking.getTelegramUserId());
                bookingTelegramBot.sendReturnedMessage(telegramUser.getTelegramId(), BOOKING_REMOVE);
            }
        }
    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getSimpleName();
    }
}
