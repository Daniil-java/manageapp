package com.kuklin.manageapp.bots.deparrbot.providers;

import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightStatsComFlightInfoProvider implements FlightInfoProvider {
    @Override
    public FlightDto getFlightInfoOrNull(String flight, String number) {
        return parseFlight(flight, number);
    }

    private FlightDto parseFlight(String flight, String number) {
        try {
            Document doc = Jsoup.connect(
                    getFlightInfoProviderOrigin() + flight + "/" + number
            ).get();

            // Авиакомпания
            String airline = textOrNull(doc.selectFirst(".eOUwOd"));
            // Аэропорты
            String departureAirport = textOrNull(doc.selectFirst(
                    ".ticket__TicketCard-sc-1rrbl5o-7.WlxJD:nth-of-type(1) .cHdMkI"
            ));
            String arrivalAirport = textOrNull(doc.selectFirst(
                    ".ticket__TicketCard-sc-1rrbl5o-7.WlxJD:nth-of-type(2) .cHdMkI"
            ));
            // Время вылета (запланированное)
            String scheduledDeparture = textOrNull(doc.selectFirst(
                    ".ticket__TicketCard-sc-1rrbl5o-7.WlxJD:nth-of-type(1) .jtsqcj:first-of-type .kbHzdx"
            ));
            // Время прибытия (запланированное)
            String scheduledArrival = textOrNull(doc.selectFirst(
                    ".ticket__TicketCard-sc-1rrbl5o-7.WlxJD:nth-of-type(2) .jtsqcj:first-of-type .kbHzdx"
            ));
            // Фактическое/оценочное прибытие
            String actualArrival = textOrNull(doc.selectFirst(
                    ".ticket__TicketCard-sc-1rrbl5o-7.WlxJD:nth-of-type(2) .jtsqcj:nth-of-type(2) .kbHzdx"
            ));
            // Status
            Element statusContainer = doc.selectFirst(".ticket__StatusContainer-sc-1rrbl5o-17");
            if (statusContainer == null) return null;
            String mainStatus = textOrNull(statusContainer.selectFirst(".iicbYn"));
            String subStatus = textOrNull(statusContainer.selectFirst(".feVjck"));

            // Терминал и гейт (берём из блока вылета)
            String terminal = textOrNull(doc.selectFirst(
                    ".ticket__TicketCard-sc-1rrbl5o-7.WlxJD:nth-of-type(1) .hkTEax .kbHzdx"
            ));
            String gate = textOrNull(doc.selectFirst(
                    ".ticket__TicketCard-sc-1rrbl5o-7.WlxJD:nth-of-type(1) .HpBbD .kbHzdx"
            ));

            log.info("{}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}",
                    actualArrival,
                    arrivalAirport,
                    departureAirport,
                    airline,
                    flight,
                    number,
                    scheduledArrival,
                    mainStatus + " " + subStatus,
                    terminal,
                    gate,
                    scheduledDeparture
            );


            return new FlightDto()
                    .setActualArrival(actualArrival)
                    .setArrivalAirport(arrivalAirport)
                    .setDepartureAirport(departureAirport)
                    .setAirline(airline)
                    .setFlight(flight)
                    .setNumber(number)
                    .setScheduledArrival(scheduledArrival)
                    .setStatus(mainStatus + " " + subStatus)
                    .setTerminal(terminal)
                    .setGate(gate)
                    .setScheduledDeparture(scheduledDeparture)
                    ;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Безопасно возвращает текст элемента или null
     */
    private String textOrNull(Element el) {
        return el != null ? el.text().trim() : null;
    }

    @Override
    public String getFlightInfoProviderOrigin() {
        return "https://www.flightstats.com/v2/flight-tracker/";
    }
}
