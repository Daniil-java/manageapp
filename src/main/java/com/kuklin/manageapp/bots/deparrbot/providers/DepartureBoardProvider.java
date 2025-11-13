package com.kuklin.manageapp.bots.deparrbot.providers;


import com.kuklin.manageapp.bots.deparrbot.models.BoardRequest;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;

import java.util.List;

public interface DepartureBoardProvider {
    List<FlightDto> getBoard(BoardRequest boardRequest);
}
