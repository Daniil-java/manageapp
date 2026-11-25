package com.kuklin.manageapp.bots.deparrbot.entities;

import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightChangesTest {

    private Flight buildStored() {
        return new Flight()
                .setFlightCode("AK").setNumber("880").setAirline("AirAsia")
                .setDepartureAirport("KUL Kuala Lumpur").setArrivalAirport("DMK Bangkok")
                .setScheduledDeparture("25.11 08:00").setExpectedDeparture("25.11 08:05")
                .setScheduledArrival("25.11 09:20").setStatus("По расписанию")
                .setTerminal("2").setGate("Q15");
    }

    private FlightDto buildDto() {
        return new FlightDto()
                .setFlight("AK").setNumber("880").setAirline("AirAsia")
                .setDepartureAirport("KUL Kuala Lumpur").setArrivalAirport("DMK Bangkok")
                .setScheduledDeparture("25.11 08:00").setExpectedDeparture("25.11 08:05")
                .setScheduledArrival("25.11 09:20").setStatus("По расписанию")
                .setTerminal("2").setGate("Q15");
    }

    @Test
    void smallExpectedDepartureShiftIsIgnored() {
        assertFalse(buildStored().hasSignificantChanges(buildDto().setExpectedDeparture("25.11 08:14")));
    }

    @Test
    void expectedDepartureShiftOfTenMinutesNotifies() {
        assertTrue(buildStored().hasSignificantChanges(buildDto().setExpectedDeparture("25.11 08:15")));
        assertTrue(buildStored().hasSignificantChanges(buildDto().setExpectedDeparture("26.11 00:30")));
    }

    @Test
    void gateChangeNotifiesWithWarning() {
        Flight stored = buildStored();
        FlightDto dto = buildDto().setGate("A6");
        assertTrue(stored.hasSignificantChanges(dto));
        assertTrue(stored.getDifference(dto).contains("Q15 -> A6"));
        assertTrue(stored.getDifference(dto).contains("табло"));
    }

    @Test
    void missingGateFromSourceIsNotAChange() {
        Flight stored = buildStored();
        FlightDto dto = buildDto().setGate(null).setTerminal(null);
        stored.fillMissingGateAndTerminal(dto);
        assertFalse(stored.hasSignificantChanges(dto));
    }

    @Test
    void freshSubscriptionRowIsNotFilledYet() {
        Flight justSubscribed = new Flight().setFlightCode("AK").setNumber("552");
        assertTrue(justSubscribed.isNotFilledYet());
        assertFalse(buildStored().isNotFilledYet());
    }

    @Test
    void oldRowWithoutExpectedDepartureUsesScheduled() {
        Flight stored = buildStored().setExpectedDeparture(null);
        assertFalse(stored.hasSignificantChanges(buildDto().setExpectedDeparture("25.11 08:08")));
        assertTrue(stored.hasSignificantChanges(buildDto().setExpectedDeparture("25.11 08:40")));
    }
}
