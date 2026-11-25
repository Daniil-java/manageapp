package com.kuklin.manageapp.bots.deparrbot.models;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class FlightDto {
    private String flight;
    private String number;
    private String airline;
    private String departureAirport;
    private String arrivalAirport;
    private String scheduledDeparture;
    /** Ожидаемое время вылета, а после вылета — фактическое */
    private String expectedDeparture;
    private String scheduledArrival;
    private String actualArrival;
    private String status;
    private String terminal;
    private String gate;

    public String getFlightInfoText() {
        StringBuilder sb = new StringBuilder();
        sb.append(getValueOrDash(airline)).append(" (" + flight + number + ")").append("\n");
        sb.append(getValueOrDash(departureAirport)).append(" -> ").append(getValueOrDash(arrivalAirport)).append("\n");
        sb.append("<b>DEP: </b>").append(getDepartureText(scheduledDeparture, expectedDeparture)).append("\n");
        sb.append("<b>ARR: </b>").append(getValueOrDash(scheduledArrival));
        if (actualArrival != null && !actualArrival.equals(scheduledArrival)) {
            sb.append(" (факт/ожид. ").append(actualArrival).append(")");
        }
        sb.append("\n");
        sb.append("<b>STATUS: </b>").append(getValueOrDash(status)).append("\n");
        sb.append("<b>TERMINAL: </b>").append(getValueOrDash(terminal)).append(" ");
        sb.append("<b>GATE: </b>").append(getValueOrDash(gate)).append("\n");

        return sb.toString();
    }

    /** "25.11 08:00" или "25.11 08:00 → ожид./факт 08:40 (+40 мин)". */
    public static String getDepartureText(String scheduled, String expected) {
        Long delay = FlightTimes.getMinutesBetweenOrNull(scheduled, expected);
        if (delay == null || delay == 0) return getValueOrDash(scheduled);
        return getValueOrDash(scheduled) + " → ожид./факт " + FlightTimes.getShortTime(scheduled, expected)
                + " (" + (delay > 0 ? "+" : "") + delay + " мин)";
    }

    public static String getValueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public static String getFlightInfoListText(List<FlightDto> flightDtos) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Расписание: </b>").append("\n\n");

        for (FlightDto info: flightDtos) {
            sb.append(info.airline).append(" (" + info.flight + info.number + ")").append("\n");
            sb.append(info.departureAirport).append(" -> ").append(info.arrivalAirport).append("\n");
            sb.append("<b>ОТПРАВ АИР: </b>").append(info.departureAirport).append("\n");
            sb.append("<b>ПРИБЫТ АИР: </b>").append(info.arrivalAirport).append("\n");
            sb.append("<b>ОТПРАВ: </b>").append(info.scheduledDeparture).append("\n");
            sb.append("<b>ПРИБЫТ: </b>").append(info.scheduledDeparture).append("\n");
        }

        return sb.toString();
    }

}
