package com.kuklin.manageapp.bots.deparrbot.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.deparrbot.models.FlightCode;
import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

import static com.kuklin.manageapp.bots.deparrbot.providers.FlightPageSupport.*;

/**
 * flightstats.com: страница рейса — Next.js, все данные лежат в {@code __NEXT_DATA__ = {...}}.
 * Читаем этот JSON, а не вёрстку: CSS-классы там генерируются и меняются при каждом деплое сайта.
 */
@Service
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class FlightStatsComFlightInfoProvider implements FlightInfoProvider {
    private static final String ORIGIN = "https://www.flightstats.com/v2/flight-tracker/";
    private static final String JSON_MARKER = "__NEXT_DATA__ = ";

    private final ObjectMapper objectMapper;

    @Override
    public FlightDto findFlightOrNull(FlightCode code) {
        try {
            String html = fetchPage(ORIGIN + code.carrier() + "/" + code.number());
            return parseFlightOrNull(html, code);
        } catch (Exception e) {
            log.warn("flightstats: не удалось получить рейс {}: {}", code.getDisplayName(), e.toString());
            return null;
        }
    }

    FlightDto parseFlightOrNull(String html, FlightCode code) throws Exception {
        String json = extractJsonObjectOrNull(html, JSON_MARKER);
        if (json == null) {
            log.warn("flightstats: на странице {} нет {}", code.getDisplayName(), JSON_MARKER.trim());
            return null;
        }
        JsonNode flight = objectMapper.readTree(json)
                .path("props").path("initialState").path("flightTracker").path("flight");
        // Рейс не найден: flight == {} или без шапки
        if (flight.path("resultHeader").isMissingNode() || flight.path("departureAirport").isMissingNode()) {
            return null;
        }

        JsonNode departure = flight.path("departureAirport");
        JsonNode arrival = flight.path("arrivalAirport");
        JsonNode schedule = flight.path("schedule");
        JsonNode status = flight.path("status");

        if (isStale(parseInstantOrNull(schedule.path("scheduledDepartureUTC").asText(null)))) {
            log.info("flightstats: для {} найден только старый рейс, пропускаем", code.getDisplayName());
            return null;
        }


        return new FlightDto()
                .setFlight(code.carrier())
                .setNumber(code.number())
                .setAirline(blankToNull(flight.path("resultHeader").path("carrier").path("name").asText(null)))
                .setDepartureAirport(buildAirport(departure.path("iata").asText(null), departure.path("city").asText(null)))
                .setArrivalAirport(buildAirport(arrival.path("iata").asText(null), arrival.path("city").asText(null)))
                .setScheduledDeparture(formatTime(schedule.path("scheduledDeparture")))
                .setExpectedDeparture(formatTime(schedule.path("estimatedActualDeparture")))
                .setScheduledArrival(formatTime(schedule.path("scheduledArrival")))
                .setActualArrival(formatTime(schedule.path("estimatedActualArrival")))
                .setStatus(getStatus(status))
                .setTerminal(blankToNull(departure.path("terminal").asText(null)))
                .setGate(blankToNull(departure.path("gate").asText(null)));
    }

    /** Коды flightstats: S — по расписанию, A — в пути, L — приземлился, C — отменён, D/R — перенаправлен. */
    private String getStatus(JsonNode status) {
        String statusCode = status.path("statusCode").asText("");
        return switch (statusCode) {
            case "A" -> STATUS_EN_ROUTE;
            case "L" -> STATUS_ARRIVED;
            case "C" -> STATUS_CANCELLED;
            case "D", "R" -> STATUS_DIVERTED;
            case "S" -> STATUS_SCHEDULED;
            default -> {
                String text = blankToNull(status.path("status").asText(null));
                yield text == null ? STATUS_SCHEDULED : text;
            }
        };
    }

    /** Время в JSON локальное для аэропорта: "2026-11-25T08:00:00.000". */
    private String formatTime(JsonNode node) {
        String value = blankToNull(node.asText(null));
        if (value == null) return null;
        try {
            return formatLocalTime(LocalDateTime.parse(value));
        } catch (Exception e) {
            return null;
        }
    }

    /** "2026-11-25T00:00:00.000Z" */
    private Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getProviderName() {
        return "flightstats";
    }
}
