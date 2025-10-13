package com.kuklin.manageapp.bots.deparrbot.repositories;

import com.kuklin.manageapp.bots.deparrbot.entities.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {
    Optional<Flight> getFlightByFlightCodeAndNumber(String flightCode, String number);
}
