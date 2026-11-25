package com.kuklin.manageapp.bots.deparrbot.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlightCodeTest {

    @Test
    void parsesIataWithAndWithoutSpace() {
        assertEquals(new FlightCode("AK", "880"), FlightCode.parseOrNull("AK 880"));
        assertEquals(new FlightCode("AK", "880"), FlightCode.parseOrNull("ak880"));
        assertEquals(new FlightCode("AK", "880"), FlightCode.parseOrNull("AK0880"));
    }

    @Test
    void parsesCarrierWithDigit() {
        assertEquals(new FlightCode("3K", "533"), FlightCode.parseOrNull("3K533"));
        assertEquals(new FlightCode("VJ", "801"), FlightCode.parseOrNull("VJ 801"));
    }

    @Test
    void convertsIcaoToIata() {
        assertEquals(new FlightCode("AK", "880"), FlightCode.parseOrNull("AXM880"));
        assertEquals("AWQ551", FlightCode.parseOrNull("QZ 551").getIcaoIdentOrNull());
    }

    @Test
    void rejectsGarbage() {
        assertNull(FlightCode.parseOrNull("AK"));
        assertNull(FlightCode.parseOrNull("AKABC"));
        assertNull(FlightCode.parseOrNull("XYZ123"));
        assertNull(FlightCode.parseOrNull("AK 12345"));
    }
}
