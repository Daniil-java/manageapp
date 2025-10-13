package com.kuklin.manageapp.bots.bookingbot.repositories;

import com.kuklin.manageapp.bots.bookingbot.entities.AvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, Long> {
    List<AvailabilityRule> findAllByBookingObjectId(Long bookingObjectId);
    // Все правила по объекту и конкретной дате (исключения)
    List<AvailabilityRule> findByBookingObjectIdAndSpecificDate(Long bookingObjectId, LocalDate date);

    // Все правила по объекту и дню недели
    List<AvailabilityRule> findByBookingObjectIdAndDayOfWeek(Long bookingObjectId, DayOfWeek dayOfWeek);

    @Query("select r.specificDate " +
            "from AvailabilityRule r " +
            "where r.bookingObjectId = :objectId " +
            "and r.specificDate is not null " +
            "and r.working = false")
    List<LocalDate> findNonWorkingSpecificDates(@Param("objectId") Long bookingObjectId);

    List<AvailabilityRule> findAllBySpecificDate(LocalDate localDate);

    List<AvailabilityRule> findAllByDayOfWeek(DayOfWeek dayOfWeek);

    List<AvailabilityRule> findByBookingObjectIdAndWorkingFalse(Long bookingObjectId);
}

