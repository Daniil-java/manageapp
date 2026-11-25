package com.kuklin.manageapp.bots.deparrbot.processors;

import com.kuklin.manageapp.bots.deparrbot.models.FlightCode;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import com.kuklin.manageapp.bots.deparrbot.entities.Flight;
import com.kuklin.manageapp.bots.deparrbot.entities.UserFlight;
import com.kuklin.manageapp.bots.deparrbot.services.FlightInfoService;
import com.kuklin.manageapp.bots.deparrbot.services.FlightService;
import com.kuklin.manageapp.bots.deparrbot.services.UserFlightService;
import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.bots.deparrbot.telegram.handlers.AviaFlightUpdateHandler;
import com.kuklin.manageapp.common.scheduler.ScheduleProcessor;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.util.ThreadUtil;
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
    private final FlightInfoService flightInfoService;
    @Override
    public void process() {
        List<Flight> flights = flightService.getAllFLight();
        for (Flight flight: flights) {
            // Ошибка на одном рейсе не должна останавливать проверку остальных
            try {
                processFlight(flight);
            } catch (Exception e) {
                log.error("Не удалось обработать подписку на рейс {}{}",
                        flight.getFlightCode(), flight.getNumber(), e);
            }
        }
    }

    private void processFlight(Flight flight) {
        //Проверка изменения состояния рейса
        FlightCode code = FlightCode.parseOrNull(flight.getFlightCode() + flight.getNumber());
        if (code == null) {
            log.warn("Не удалось разобрать номер рейса {}{}", flight.getFlightCode(), flight.getNumber());
            return;
        }
        FlightDto info = flightInfoService.findFlightOrNull(code);
        if (info == null) {
            return;
        }
        // Подписка создаёт рейс только с номером. Первое заполнение сохраняем молча:
        // иначе подписчик получал «изменения» вида "— -> KUL" через несколько минут после подписки
        if (flight.isNotFilledYet()) {
            flightService.updateFlight(flight, info);
            return;
        }
        flight.fillMissingGateAndTerminal(info);
        //Если существенных изменений нет - ничего не сохраняем, чтобы мелкие сдвиги времени накапливались
        if (!flight.hasSignificantChanges(info)) {
            return;
        }
        String difference = flight.getDifference(info);
        //Если состояние изменилось - сохраняем новое
        Flight updatedFlight = flightService.updateFlight(flight, info);
        List<UserFlight> userFlights =
                userFlightService.getUsersFlightByFlightId(updatedFlight.getId());

        for (UserFlight userFlight: userFlights) {
            aviaTelegramBot.sendReturnedMessage(
                    userFlight.getTelegramId(),
                    difference,
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

    @Override
    public String getSchedulerName() {
        return this.getClass().getSimpleName();
    }
}
