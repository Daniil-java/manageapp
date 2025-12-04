package com.kuklin.manageapp.bots.bookingbot.services;

import com.kuklin.manageapp.bots.bookingbot.entities.AvailabilityRule;
import com.kuklin.manageapp.bots.bookingbot.repositories.AvailabilityRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {
    private final AvailabilityRuleRepository repository;

    /**
     * Найти правило для объекта
     */
    public List<AvailabilityRule> getByBookingObjectId(Long bookingObjectId) {
        return repository.findAllByBookingObjectId(bookingObjectId);
    }

    /**
     * Сохранить или обновить правило
     */
    public AvailabilityRule saveOrNull(AvailabilityRule availabilityRule) {
        return repository.save(availabilityRule);
    }

    /**
     * Найти правило для конкретной даты
     * 1. Сначала ищем исключение (specificDate)
     * 2. Если нет — ищем по дню недели
     */
    private Optional<AvailabilityRule> findRuleForDate(Long bookingObjectId, LocalDate date) {
        // 1. Проверяем исключения
        List<AvailabilityRule> specific = repository
                .findByBookingObjectIdAndSpecificDate(bookingObjectId, date);
        if (!specific.isEmpty()) {
            return Optional.of(specific.get(0));
        }

        // 2. Ищем по дню недели
        List<AvailabilityRule> weekly = repository
                .findByBookingObjectIdAndDayOfWeek(bookingObjectId, date.getDayOfWeek());
        if (!weekly.isEmpty()) {
            return Optional.of(weekly.get(0));
        }

        return Optional.empty();
    }

    /**
     * Проверить, доступен ли объект в указанную дату и время
     */
    public boolean isAvailable(Long bookingObjectId, LocalDate date, LocalTime time) {
        return findRuleForDate(bookingObjectId, date)
                .filter(rule -> rule.isWorking()
                        && !time.isBefore(rule.getStartTime())
                        && !time.isAfter(rule.getEndTime()))
                .isPresent();
    }

    public List<LocalDate> getNonWorkingSpecificDates(Long bookingObjectId) {
        return repository.findNonWorkingSpecificDates(bookingObjectId);
    }

    public List<AvailabilityRule> getAllBySpecificDate(LocalDate localDate) {
        return repository.findAllBySpecificDate(localDate);
    }

    public List<AvailabilityRule> getAllByDayOfWeek(DayOfWeek dayOfWeek) {
        return repository.findAllByDayOfWeek(dayOfWeek);
    }

    public List<AvailabilityRule> findByBookingObjectIdAndDate(Long bookingObjectId, LocalDate localDate) {
        List<AvailabilityRule> specific = repository.findByBookingObjectIdAndSpecificDate(bookingObjectId, localDate);
        if (!specific.isEmpty()) {
            return specific;
        }
        return repository.findByBookingObjectIdAndDayOfWeek(bookingObjectId, localDate.getDayOfWeek());
    }

    public List<DayOfWeek> getNonWorkingDaysOfWeek(Long bookingObjectId) {
        List<AvailabilityRule> rules = repository
                .findByBookingObjectIdAndWorkingFalse(bookingObjectId);

        // Преобразуем строки в enum DayOfWeek
        return rules.stream()
                .map(AvailabilityRule::getDayOfWeek) // на всякий случай
                .toList();
    }

}
