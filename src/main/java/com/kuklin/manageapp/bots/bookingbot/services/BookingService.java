package com.kuklin.manageapp.bots.bookingbot.services;

import com.kuklin.manageapp.bots.bookingbot.entities.AvailabilityRule;
import com.kuklin.manageapp.bots.bookingbot.entities.Booking;
import com.kuklin.manageapp.bots.bookingbot.repositories.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {
    private final BookingRepository repository;
    private final AvailabilityService availabilityService;
    private final GoogleSheetsService googleSheetsService;

    /**
     * Создать или обновить бронь
     */
    public Booking addNewBookOrNull(Booking booking) {
        booking = repository.save(booking);
        if (booking.getStatus().equals(Booking.Status.BOOKED)) {
            try {
                googleSheetsService.appendBooking(booking);
            } catch (IOException e) {
                log.error("Google Sheets append booking error!");
                return null;
            }
        }
        return booking;
    }

    @Transactional
    public Booking updateStatus(Booking booking, Booking.Status status) {
        booking = repository.save(booking.setStatus(status));
        try {
            googleSheetsService.updateBookingStatus(booking.getId(), booking.getStatus());
        } catch (IOException e) {
            log.error("Не получилось обновить статус записи в таблице");
            throw new RuntimeException(e);
        }
        return booking;
    }

    @Transactional
    public Booking saveAndUnbookOtherBookProcessing(Booking booking) {
        repository.deleteAllByTelegramUserIdAndStatus(booking.getTelegramUserId(), Booking.Status.BOOKING);
        return repository.save(booking);
    }

    public List<Booking> findByTelegramUserIdAndStatus(Long telegramUserId, Booking.Status status) {
        return repository.findAllByTelegramUserIdAndStatus(telegramUserId, status);
    }

    /**
     * Найти все брони объекта
     */
    public List<Booking> findByBookingObjectId(Long bookingObjectId) {
        return repository.findByBookingObjectId(bookingObjectId);
    }

    /**
     * Удалить бронь
     */
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public List<LocalDate> getReservedDates(Long bookingObjectId) {
        // 1. Все даты, где есть брони
        List<LocalDate> bookedDates = repository.findDatesByStatusRaw(bookingObjectId, Booking.Status.BOOKED)
                .stream()
                .map(java.sql.Date::toLocalDate)
                .toList();

        List<LocalDate> fullyReserved = new ArrayList<>();

        for (LocalDate date : bookedDates) {
            // 2. Находим правило для этой даты
            List<AvailabilityRule> rules = availabilityService.findByBookingObjectIdAndDate(bookingObjectId, date);
            if (rules.isEmpty()) continue;

            // Определяем рабочее окно (minStart, maxEnd, slotMinutes)
            LocalTime minStart = rules.stream().map(AvailabilityRule::getStartTime).min(LocalTime::compareTo).orElse(null);
            LocalTime maxEnd   = rules.stream().map(AvailabilityRule::getEndTime).max(LocalTime::compareTo).orElse(null);
            int slotMinutes    = rules.stream().map(AvailabilityRule::getSlotDurationMinutes).max(Integer::compare).orElse(30);

            // 3. Генерируем все слоты
            List<LocalTime[]> slots = new ArrayList<>();
            for (LocalTime t = minStart; t.plusMinutes(slotMinutes).compareTo(maxEnd) <= 0; t = t.plusMinutes(slotMinutes)) {
                slots.add(new LocalTime[]{t, t.plusMinutes(slotMinutes)});
            }

            // 4. Достаём все брони на этот день
            List<Booking> bookings = repository.findByBookingObjectIdAndStartTimeBetween(
                    bookingObjectId,
                    date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay()
            );

            // 5. Фильтруем свободные слоты
            List<LocalTime[]> freeSlots = slots.stream()
                    .filter(slot -> bookings.stream().noneMatch(b ->
                            b.getStatus() == Booking.Status.BOOKED &&
                                    b.getStartTime().toLocalTime().isBefore(slot[1]) &&
                                    b.getEndTime().toLocalTime().isAfter(slot[0])
                    ))
                    .toList();

            // 6. Если свободных слотов нет → день полностью занят
            if (freeSlots.isEmpty()) {
                fullyReserved.add(date);
            }
        }

        return fullyReserved;
    }

    public List<LocalTime[]> getAvailableSlots(Long bookingObjectId, LocalDate date,
                                               LocalTime minStart, LocalTime maxEnd, int slotMinutes) {
        // 1. Сгенерировать все слоты
        List<LocalTime[]> slots = new ArrayList<>();
        for (LocalTime t = minStart; t.plusMinutes(slotMinutes).compareTo(maxEnd) <= 0; t = t.plusMinutes(slotMinutes)) {
            slots.add(new LocalTime[]{t, t.plusMinutes(slotMinutes)});
        }

        // 2. Достать брони на этот день
        List<Booking> bookings = repository.findByBookingObjectIdAndStartTimeBetween(
                bookingObjectId,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );

        // 3. Фильтровать занятые
        Stream<LocalTime[]> stream = slots.stream()
                .filter(slot -> bookings.stream().noneMatch(b ->
                        b.getStatus() == Booking.Status.BOOKED &&
                                b.getStartTime().toLocalTime().isBefore(slot[1]) &&
                                b.getEndTime().toLocalTime().isAfter(slot[0])
                ));

        // 4. Если это сегодня — убрать слоты до текущего времени + шаг
        if (date.isEqual(LocalDate.now())) {
            LocalTime now = LocalTime.now();
            LocalTime threshold = now.plusMinutes(slotMinutes);

            // если threshold >= maxEnd → день уже закончился
            if (!threshold.isBefore(maxEnd)) {
                return List.of();
            }

            stream = stream.filter(slot ->
                    // слот начинается после текущего времени
                    slot[0].isAfter(now)
                            // и не раньше порога (но равен порогу допускаем)
                            && (slot[0].equals(threshold) || slot[0].isAfter(threshold))
            );
        }
        return stream.toList();
    }

    public Booking getBookingByUserIdAndStatusBookingOrNull(Long userId) {
        return repository.findByTelegramUserIdAndStatus(userId, Booking.Status.BOOKING)
                .orElse(null);
    }

    @Transactional
    public Booking cancelBookingByIdOrNull(Long bookingId) throws RuntimeException {
        Booking booking = repository.findById(bookingId).get();
        if (booking == null) return null;

        Booking updatedBooking = repository.save(booking.setStatus(Booking.Status.CANCELLED));
        try {
            googleSheetsService.updateBookingStatus(updatedBooking.getId(), updatedBooking.getStatus());
        } catch (IOException e) {
            log.error("Не получилось обновить запись в таблице!");
            throw new RuntimeException(e);
        }

        return booking;
    }

    public List<Booking> getAllBookingsByStatus(Booking.Status status) {
        return repository.findAllByStatus(status);
    }

}
