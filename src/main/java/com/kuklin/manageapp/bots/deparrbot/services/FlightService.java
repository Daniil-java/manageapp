package com.kuklin.manageapp.bots.deparrbot.services;

import com.kuklin.manageapp.bots.deparrbot.entities.Flight;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import com.kuklin.manageapp.bots.deparrbot.repositories.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlightService {
    private final FlightRepository flightRepository;

    public Flight createFlight(String flightCode, String number) {
        flightCode = flightCode.toUpperCase();
        number = number.toUpperCase();
        Flight flight = new Flight()
                .setFlightCode(flightCode)
                .setNumber(number)
                ;

        return flightRepository.save(flight);
    }

    public Long getFlightIdOrNull(String flightCode, String number) {
        return flightRepository.getFlightByFlightCodeAndNumber(flightCode, number)
                .map(Flight::getId)
                .orElse(null);
    }

    public void removeByFlightId(Long flightId) {
        flightRepository.deleteById(flightId);
    }

    public List<Flight> getAllFLight() {
        return flightRepository.findAll();
    }

    public Flight updateFlight(Flight flight, FlightDto flightDto) {
        Flight newFlight = Flight.toEntity(flightDto).setId(flight.getId());
        newFlight.setFlightCode(newFlight.getFlightCode().toUpperCase());
        newFlight.setNumber(newFlight.getNumber().toUpperCase());

        return flightRepository.save(newFlight);
    }
}
