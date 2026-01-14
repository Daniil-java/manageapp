package com.kuklin.manageapp.bots.bookingbot.repositories;

import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingObjectRepository extends JpaRepository<BookingObject, Long> {
}
