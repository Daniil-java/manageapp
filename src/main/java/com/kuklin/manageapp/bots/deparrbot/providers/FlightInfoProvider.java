package com.kuklin.manageapp.bots.deparrbot.providers;


import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;

public interface FlightInfoProvider {
    FlightDto getFlightInfoOrNull(String flight, String number);

    String getFlightInfoProviderOrigin();
}
