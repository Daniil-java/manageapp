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
    private String scheduledArrival;
    private String actualArrival;
    private String status;
    private String terminal;
    private String gate;

    public String getFlightInfoText() {
        StringBuilder sb = new StringBuilder();
        sb.append(airline).append(" (" + flight + number + ")").append("\n");
        sb.append(departureAirport).append(" -> ").append(arrivalAirport).append("\n");
        sb.append("<b>DEP: </b>").append(scheduledDeparture).append("\n");
        sb.append("<b>ARR: </b>").append(scheduledArrival).append("\n");
        sb.append("<b>STATUS: </b>").append(status).append("\n");
        sb.append("<b>TERMINAL: </b>").append(terminal).append(" ");
        sb.append("<b>GATE: </b>").append(gate).append("\n");

        return sb.toString();
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
