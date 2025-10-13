package com.kuklin.manageapp.bots.deparrbot.processors;

import com.kuklin.manageapp.bots.deparrbot.entities.Flight;
import com.kuklin.manageapp.bots.deparrbot.entities.UserFlight;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import com.kuklin.manageapp.bots.deparrbot.providers.FlightStatsComFlightInfoProvider;
import com.kuklin.manageapp.bots.deparrbot.services.FlightService;
import com.kuklin.manageapp.bots.deparrbot.services.UserFlightService;
import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.bots.deparrbot.telegram.handlers.AviaFlightUpdateHandler;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlightSubNotificationScheduleProcessor implements ScheduleProcessor {
    private final UserFlightService userFlightService;
    private final FlightService flightService;
    private final AviaTelegramBot aviaTelegramBot;
    private final FlightStatsComFlightInfoProvider flightInfoProvider;
    @Override
    public void process() {
        List<Flight> flights = flightService.getAllFLight();
        for (Flight flight: flights) {
            //Проверка изменения состояния рейса
            FlightDto info = flightInfoProvider
                    .getFlightInfoOrNull(flight.getFlightCode(), flight.getNumber());
            //Если состояние не изменилось - переходим к следующему рейсу
            if (info == null || flight.equalsDto(info)) {
                continue;
            }
            //Если состояние изменилось - сохраняем новое
            Flight updatedFlight = flightService.updateFlight(flight, info);
            List<UserFlight> userFlights =
                    userFlightService.getUsersFlightByFlightId(updatedFlight.getId());

            for (UserFlight userFlight: userFlights) {
                aviaTelegramBot.sendReturnedMessage(
                        userFlight.getTelegramId(),
                        flight.getDifference(info),
                        AviaFlightUpdateHandler.getInlineMessageFlight(
                                updatedFlight.getFlightCode(),
                                updatedFlight.getNumber(),
                                Command.AVIA_UNSUBSCRIBE,
                                Command.AVIA_UNSUBSCRIBE.getCommandText()),
                        null
                );
                ThreadUtil.sleep(100);
            }
        }

    }

    @Override
    public String getSchedulerName() {
        return this.getClass().getSimpleName();
    }
}
