package com.kuklin.manageapp.bots.bookingbot.repositories;

import com.kuklin.manageapp.bots.bookingbot.entities.FormAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FormAnswerRepository extends JpaRepository<FormAnswer, Long> {
    // Найти анкету по id брони
    Optional<FormAnswer> findByBookingId(Long bookingId);

    @EntityGraph(attributePaths = "booking")
    Optional<FormAnswer> findWithBookingByBookingId(Long bookingId);

}
