package com.kuklin.manageapp.bots.deparrbot.services;


import com.kuklin.manageapp.bots.deparrbot.entities.UserFlight;
import com.kuklin.manageapp.bots.deparrbot.repositories.UserFlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserFlightService {
    private final UserFlightRepository userFlightRepository;
    private final FlightService flightService;

    public UserFlight subscribeUserToFlight(Long telegramId, Long flightId) {
        UserFlight uf = new UserFlight()
                .setTelegramId(telegramId)
                .setFlightId(flightId)
                .setNotificationsEnabled(true);
        return userFlightRepository.save(uf);
    }

    public List<UserFlight> getFlightsByUser(Long telegramId) {
        return userFlightRepository.findByTelegramId(telegramId);
    }

    public List<UserFlight> getUsersFlightByFlightId(Long flightId) {
        return userFlightRepository.findByFlightId(flightId);
    }

    public List<UserFlight> getAllUsersFlights() {
        return userFlightRepository.findAll();
    }

    public Boolean isUserSubscribe(String flightCode, String number, Long telegramId) {
        Long flightId = flightService.getFlightIdOrNull(flightCode, number);
        if (flightId == null) return false;

        return userFlightRepository.existsUserFlightByFlightIdAndTelegramId(flightId, telegramId);
    }

    public UserFlight subscribeUserToFlight(Long telegramId, String flightCode, String number) {

        //Проверяем существует ли такой рейс в базе, если нет - то создаем
        Long flightId = flightService.getFlightIdOrNull(flightCode, number);
        if (flightId == null) {
            flightId = flightService.createFlight(flightCode, number).getId();
        }

        //Если юзер уже подписан, возврощаем старый результат.
        List<UserFlight> userFlights = userFlightRepository.findAllByFlightIdAndTelegramId(flightId, telegramId);
        if (!userFlights.isEmpty()) {
            return userFlights.get(0);
        }

        return subscribeUserToFlight(telegramId, flightId);
    }

    public void unsubscribeUserToFlight(Long telegramId, String flightCode, String number) {
        //Проверяем существует ли такой рейс в базе, если нет - то прерываем выполение
        Long flightId = flightService.getFlightIdOrNull(flightCode, number);
        if (flightId == null) {
            return;
        }

        //Если юзер подписан, удаляем все записи
        List<UserFlight> userFlights = userFlightRepository.findAllByFlightIdAndTelegramId(flightId, telegramId);
        if (!userFlights.isEmpty()) {
            userFlightRepository.deleteAll(userFlights);
        }

        //Удаляем запись о рейсе, если на него никто не подписан;
        userFlights = userFlightRepository.findByFlightId(flightId);
        if (userFlights.isEmpty()) {
            flightService.removeByFlightId(flightId);
        }

    }
}
