package com.kuklin.manageapp.bots.bookingbot.repositories;

import com.kuklin.manageapp.bots.bookingbot.entities.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByTelegramUserId(Long telegramUserId);
    List<Booking> findAllByTelegramUserIdAndStatus(Long telegramUserId, Booking.Status status);

    List<Booking> findByBookingObjectId(Long bookingObjectId);

    List<Booking> findByStatus(Booking.Status status);

    // Найти все брони по id объекта и в промежутке времени
    List<Booking> findByBookingObjectIdAndStartTimeBetween(
            Long bookingObjectId,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("select distinct function('date', b.startTime) " +
            "from Booking b " +
            "where b.bookingObjectId = :objectId " +
            "and b.status = :status")
    List<java.sql.Date> findDatesByStatusRaw(
            @Param("objectId") Long bookingObjectId,
            @Param("status") Booking.Status status
    );

    void deleteAllByTelegramUserIdAndStatus(Long teleramUserId, Booking.Status status);

    Optional<Booking> findByTelegramUserIdAndStatus(Long telegramUserId, Booking.Status status);

    List<Booking> findAllByStatus(Booking.Status status);
}
