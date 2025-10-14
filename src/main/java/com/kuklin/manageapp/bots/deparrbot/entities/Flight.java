package com.kuklin.manageapp.bots.deparrbot.entities;

import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "flights")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Flight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "flight_code")
    private String flightCode;
    private String number;

    private String airline;
    @Column(name = "departure_airport")
    private String departureAirport;
    @Column(name = "arrival_airport")
    private String arrivalAirport;
    @Column(name = "scheduled_departure")
    private String scheduledDeparture;
    @Column(name = "scheduled_arrival")
    private String scheduledArrival;
    @Column(name = "actual_arrival")
    private String actualArrival;
    private String status;
    private String terminal;
    private String gate;

    @PrePersist
    @PreUpdate
    public void normalize() {
        if (flightCode != null) {
            flightCode = flightCode.toUpperCase();
        }
        if (number != null) {
            number = number.toUpperCase();
        }
    }


    public static Flight toEntity(FlightDto dto) {
        if (dto == null) {
            return null;
        }

        Flight flight = new Flight();
        flight.setFlightCode(dto.getFlight());
        flight.setNumber(dto.getNumber());
        flight.setAirline(dto.getAirline());
        flight.setDepartureAirport(dto.getDepartureAirport());
        flight.setArrivalAirport(dto.getArrivalAirport());
        flight.setScheduledDeparture(dto.getScheduledDeparture());
        flight.setScheduledArrival(dto.getScheduledArrival());
        flight.setActualArrival(dto.getActualArrival());
        flight.setStatus(dto.getStatus());
        flight.setTerminal(dto.getTerminal());
        flight.setGate(dto.getGate());

        return flight;
    }

    public boolean equalsDto(FlightDto dto) {
        if (dto == null) return false;

        return equalsIgnoreCase(this.flightCode, dto.getFlight()) &&
                equalsIgnoreCase(this.number, dto.getNumber()) &&
                equalsIgnoreCase(this.airline, dto.getAirline()) &&
                equalsIgnoreCase(this.departureAirport, dto.getDepartureAirport()) &&
                equalsIgnoreCase(this.arrivalAirport, dto.getArrivalAirport()) &&
                equalsIgnoreCase(this.scheduledDeparture, dto.getScheduledDeparture()) &&
                equalsIgnoreCase(this.scheduledArrival, dto.getScheduledArrival()) &&
                equalsIgnoreCase(this.status, dto.getStatus()) &&
                equalsIgnoreCase(this.terminal, dto.getTerminal()) &&
                equalsIgnoreCase(this.gate, dto.getGate());
    }

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    public String getDifference(FlightDto dto) {
        String flight = getDifferenceString(flightCode + number, dto.getFlight() + dto.getNumber());
        StringBuilder sb = new StringBuilder();
        sb.append(airline).append(" (" + flight + ")").append("\n");

        sb.append(getDifferenceString(departureAirport, dto.getDepartureAirport()))
                .append(" -> ")
                .append(getDifferenceString(arrivalAirport, dto.getArrivalAirport()))
                .append("\n");

        sb.append("<b>DEP: </b>").append(getDifferenceString(scheduledDeparture, dto.getScheduledDeparture())).append("\n");
        sb.append("<b>ARR: </b>").append(getDifferenceString(scheduledArrival, dto.getScheduledArrival())).append("\n");
        sb.append("<b>STATUS: </b>").append(getDifferenceString(status, dto.getStatus())).append("\n");
        sb.append("<b>TERMINAL: </b>").append(getDifferenceString(terminal, dto.getTerminal())).append(" ");
        sb.append("<b>GATE: </b>").append(getDifferenceString(gate, dto.getGate())).append("\n");

        return sb.toString();
    }

    private String getDifferenceString(String s1, String s2) {
        return s1.equals(s2) ? s1
                : s1 + " -> " + s2
                ;
    }

    public String getFlightInfoText() {

        StringBuilder sb = new StringBuilder();
        sb.append(airline).append(" (" + flightCode + number + ")").append("\n");
        sb.append(departureAirport).append(" -> ").append(arrivalAirport).append("\n");
        sb.append("<b>DEP: </b>").append(scheduledDeparture).append("\n");
        sb.append("<b>ARR: </b>").append(scheduledArrival).append("\n");
        sb.append("<b>STATUS: </b>").append(status).append("\n");
        sb.append("<b>TERMINAL: </b>").append(terminal).append(" ");
        sb.append("<b>GATE: </b>").append(gate).append("\n");

        return sb.toString();
    }


}
