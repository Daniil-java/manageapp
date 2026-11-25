package com.kuklin.manageapp.bots.deparrbot.models;

import com.kuklin.manageapp.bots.deparrbot.entities.Flight;

/**
 * Рейс для REST API (виджет). Время — локальное время аэропорта, строкой "dd.MM HH:mm".
 * departureDelayMinutes — насколько ожидаемый/фактический вылет позже планового (null, если неизвестно).
 */
public record AviaFlightResponse(
        String flight,
        String airline,
        String departureAirport,
        String arrivalAirport,
        String scheduledDeparture,
        String expectedDeparture,
        Long departureDelayMinutes,
        String scheduledArrival,
        String expectedArrival,
        String status,
        String terminal,
        String gate
) {
    public static AviaFlightResponse fromDto(FlightDto dto) {
        return new AviaFlightResponse(
                dto.getFlight() + dto.getNumber(),
                dto.getAirline(),
                dto.getDepartureAirport(),
                dto.getArrivalAirport(),
                dto.getScheduledDeparture(),
                dto.getExpectedDeparture(),
                FlightTimes.getMinutesBetweenOrNull(dto.getScheduledDeparture(), dto.getExpectedDeparture()),
                dto.getScheduledArrival(),
                dto.getActualArrival(),
                dto.getStatus(),
                dto.getTerminal(),
                dto.getGate());
    }

    public static AviaFlightResponse fromEntity(Flight flight) {
        return new AviaFlightResponse(
                flight.getFlightCode() + flight.getNumber(),
                flight.getAirline(),
                flight.getDepartureAirport(),
                flight.getArrivalAirport(),
                flight.getScheduledDeparture(),
                flight.getExpectedDeparture(),
                FlightTimes.getMinutesBetweenOrNull(flight.getScheduledDeparture(), flight.getExpectedDeparture()),
                flight.getScheduledArrival(),
                flight.getActualArrival(),
                flight.getStatus(),
                flight.getTerminal(),
                flight.getGate());
    }
}
