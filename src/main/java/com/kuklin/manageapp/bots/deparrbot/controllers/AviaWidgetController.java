package com.kuklin.manageapp.bots.deparrbot.controllers;

import com.kuklin.manageapp.bots.deparrbot.models.AviaFlightResponse;
import com.kuklin.manageapp.bots.deparrbot.models.FlightCode;
import com.kuklin.manageapp.bots.deparrbot.services.AviaApiTokenService;
import com.kuklin.manageapp.bots.deparrbot.services.AviaWidgetService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API авиабота для Android-виджета.
 * Авторизация — личный токен из команды /token в боте, заголовок X-Avia-Token.
 * Код рейса в пути — как в боте: AK880, AK0880, AXM880.
 *
 * Ответы 401/400/404 отдаём через ResponseEntity: общий обработчик ошибок
 * превращает любое исключение в 500.
 */
@RestController
@RequestMapping("/avia")
@RequiredArgsConstructor
@Hidden
public class AviaWidgetController {
    public static final String TOKEN_HEADER = "X-Avia-Token";

    private final AviaApiTokenService aviaApiTokenService;
    private final AviaWidgetService aviaWidgetService;

    /** Рейсы, на которые подписан пользователь (данные из БД, обновляются раз в 10 минут). */
    @GetMapping("/subscriptions")
    public ResponseEntity<List<AviaFlightResponse>> getSubscriptions(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        Long telegramId = aviaApiTokenService.findTelegramIdOrNull(token);
        if (telegramId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(aviaWidgetService.getSubscriptions(telegramId));
    }

    /** Свежая информация о рейсе с сайтов (несколько секунд). */
    @GetMapping("/flights/{code}")
    public ResponseEntity<AviaFlightResponse> getFlight(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @PathVariable("code") String code) {
        return handleFlightRequest(token, code, (telegramId, flightCode) -> aviaWidgetService.findFlightOrNull(flightCode));
    }

    @PostMapping("/subscriptions/{code}")
    public ResponseEntity<AviaFlightResponse> subscribe(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @PathVariable("code") String code) {
        return handleFlightRequest(token, code, aviaWidgetService::subscribeOrNull);
    }

    @DeleteMapping("/subscriptions/{code}")
    public ResponseEntity<Void> unsubscribe(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @PathVariable("code") String code) {
        Long telegramId = aviaApiTokenService.findTelegramIdOrNull(token);
        if (telegramId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        FlightCode flightCode = FlightCode.parseOrNull(code);
        if (flightCode == null) return ResponseEntity.badRequest().build();

        aviaWidgetService.unsubscribe(telegramId, flightCode);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AviaFlightResponse> handleFlightRequest(
            String token, String code, FlightAction action) {
        Long telegramId = aviaApiTokenService.findTelegramIdOrNull(token);
        if (telegramId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        FlightCode flightCode = FlightCode.parseOrNull(code);
        if (flightCode == null) return ResponseEntity.badRequest().build();

        AviaFlightResponse response = action.apply(telegramId, flightCode);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    @FunctionalInterface
    private interface FlightAction {
        AviaFlightResponse apply(Long telegramId, FlightCode code);
    }
}
