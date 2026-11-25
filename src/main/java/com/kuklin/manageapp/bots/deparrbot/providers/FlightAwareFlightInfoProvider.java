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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Iterator;

import static com.kuklin.manageapp.bots.deparrbot.providers.FlightPageSupport.*;

/**
 * flightaware.com: запасной источник. Данные лежат в {@code var trackpollBootstrap = {...}}.
 * Ищет только по ICAO-идентификатору (AXM880), поэтому нужен перевод через AirlineCodes.
 */
@Service
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class FlightAwareFlightInfoProvider implements FlightInfoProvider {
    private static final String ORIGIN = "https://www.flightaware.com/live/flight/";
    private static final String JSON_MARKER = "trackpollBootstrap";

    private final ObjectMapper objectMapper;

    @Override
    public FlightDto findFlightOrNull(FlightCode code) {
        String ident = code.getIcaoIdentOrNull();
        if (ident == null) {
            log.info("flightaware: нет ICAO-кода для перевозчика {}", code.carrier());
            return null;
        }
        try {
            return parseFlightOrNull(fetchPage(ORIGIN + ident), code);
        } catch (Exception e) {
            log.warn("flightaware: не удалось получить рейс {}: {}", ident, e.toString());
            return null;
        }
    }

    FlightDto parseFlightOrNull(String html, FlightCode code) throws Exception {
        String json = extractJsonObjectOrNull(html, JSON_MARKER);
        if (json == null) return null;

        Iterator<JsonNode> flights = objectMapper.readTree(json).path("flights").elements();
        if (!flights.hasNext()) return null;
        JsonNode flight = flights.next();

        JsonNode origin = flight.path("origin");
        JsonNode destination = flight.path("destination");
        if (flight.path("resultUnknown").asBoolean(false)
                || origin.path("iata").isMissingNode() || destination.path("iata").isMissingNode()) {
            return null;
        }

        ZoneId originZone = getZone(origin);
        ZoneId destinationZone = getZone(destination);
        JsonNode gateDeparture = flight.path("gateDepartureTimes");
        JsonNode gateArrival = flight.path("gateArrivalTimes");

        long scheduledDeparture = gateDeparture.path("scheduled").asLong(0);
        if (scheduledDeparture > 0 && isStale(Instant.ofEpochSecond(scheduledDeparture))) {
            log.info("flightaware: для {} найден только старый рейс, пропускаем", code.getDisplayName());
            return null;
        }
        return new FlightDto()
                .setFlight(code.carrier())
                .setNumber(code.number())
                .setAirline(blankToNull(flight.path("airline").path("shortName").asText(null)))
                .setDepartureAirport(buildAirport(origin.path("iata").asText(null), getCity(origin)))
                .setArrivalAirport(buildAirport(destination.path("iata").asText(null), getCity(destination)))
                .setScheduledDeparture(formatEpochTime(scheduledDeparture, originZone))
                .setExpectedDeparture(formatEpochTime(getActualOrEstimated(gateDeparture), originZone))
                .setScheduledArrival(formatEpochTime(gateArrival.path("scheduled").asLong(0), destinationZone))
                .setActualArrival(formatEpochTime(getActualOrEstimated(gateArrival), destinationZone))
                .setStatus(getStatus(flight))
                .setTerminal(blankToNull(origin.path("terminal").asText(null)))
                .setGate(blankToNull(origin.path("gate").asText(null)));
    }

    private String getStatus(JsonNode flight) {
        if (flight.path("cancelled").asBoolean(false)) return STATUS_CANCELLED;
        if (flight.path("diverted").asBoolean(false)) return STATUS_DIVERTED;
        if ("arrived".equalsIgnoreCase(flight.path("flightStatus").asText(""))
                || flight.path("gateArrivalTimes").path("actual").asLong(0) > 0
                || flight.path("landingTimes").path("actual").asLong(0) > 0) {
            return STATUS_ARRIVED;
        }
        if (flight.path("gateDepartureTimes").path("actual").asLong(0) > 0
                || flight.path("takeoffTimes").path("actual").asLong(0) > 0) {
            return STATUS_EN_ROUTE;
        }
        return STATUS_SCHEDULED;
    }

    private long getActualOrEstimated(JsonNode times) {
        long actual = times.path("actual").asLong(0);
        return actual > 0 ? actual : times.path("estimated").asLong(0);
    }

    /** "Kuala Lumpur, Malaysia" → "Kuala Lumpur", чтобы совпадало с flightstats. */
    private String getCity(JsonNode airport) {
        String location = blankToNull(airport.path("friendlyLocation").asText(null));
        if (location == null) return null;
        int comma = location.indexOf(',');
        return comma < 0 ? location : location.substring(0, comma).trim();
    }

    /** TZ приходит как ":Asia/Bangkok". */
    private ZoneId getZone(JsonNode airport) {
        String tz = airport.path("TZ").asText("").replace(":", "");
        try {
            return tz.isEmpty() ? ZoneOffset.UTC : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    @Override
    public String getProviderName() {
        return "flightaware";
    }
}
