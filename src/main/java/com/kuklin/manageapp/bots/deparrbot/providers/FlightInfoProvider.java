package com.kuklin.manageapp.bots.deparrbot.providers;


import com.kuklin.manageapp.bots.deparrbot.models.FlightCode;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;

/**
 * Источник данных о рейсе. Реализации опрашиваются по очереди (по @Order),
 * пока одна из них не найдёт рейс, см. FlightInfoService.
 */
public interface FlightInfoProvider {
    FlightDto findFlightOrNull(FlightCode code);

    String getProviderName();
}
