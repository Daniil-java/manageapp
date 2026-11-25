package com.kuklin.manageapp.bots.deparrbot.services;

import com.kuklin.manageapp.bots.deparrbot.entities.Flight;
import com.kuklin.manageapp.bots.deparrbot.models.AviaFlightResponse;
import com.kuklin.manageapp.bots.deparrbot.models.FlightCode;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Логика REST API авиабота для виджета: те же подписки, что и в боте.
 */
@Service
@RequiredArgsConstructor
public class AviaWidgetService {
    private final FlightInfoService flightInfoService;
    private final FlightService flightService;
    private final UserFlightService userFlightService;

    /** Подписки из БД — их раз в 10 минут обновляет планировщик, сайты не дёргаем. */
    public List<AviaFlightResponse> getSubscriptions(Long telegramId) {
        return userFlightService.getSubscribedFlights(telegramId).stream()
                .sorted(Comparator.comparing(Flight::getId))
                .map(AviaFlightResponse::fromEntity)
                .toList();
    }

    /** Свежие данные с сайтов. */
    public AviaFlightResponse findFlightOrNull(FlightCode code) {
        FlightDto dto = flightInfoService.findFlightOrNull(code);
        return dto == null ? null : AviaFlightResponse.fromDto(dto);
    }

    /**
     * Подписка как кнопкой в боте. Новый рейс сразу заполняем свежими данными,
     * чтобы виджет не показывал пустую карточку до прохода планировщика.
     * Уже отслеживаемый рейс не трогаем: иначе изменение (например, гейта) сохранилось бы
     * молча и другие подписчики не получили бы уведомление.
     */
    public AviaFlightResponse subscribeOrNull(Long telegramId, FlightCode code) {
        FlightDto dto = flightInfoService.findFlightOrNull(code);
        if (dto == null) return null;

        userFlightService.subscribeUserToFlight(telegramId, code.carrier(), code.number());
        Flight flight = flightService.findFlightOrNull(code.carrier(), code.number());
        if (flight != null && flight.isNotFilledYet()) {
            flightService.updateFlight(flight, dto);
        }
        return AviaFlightResponse.fromDto(dto);
    }

    public void unsubscribe(Long telegramId, FlightCode code) {
        userFlightService.unsubscribeUserToFlight(telegramId, code.carrier(), code.number());
    }
}
