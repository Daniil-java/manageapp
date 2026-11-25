package com.kuklin.manageapp.bots.deparrbot.entities;

import com.kuklin.manageapp.bots.deparrbot.models.FlightDto;
import com.kuklin.manageapp.bots.deparrbot.models.FlightTimes;
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
    private static final long EXPECTED_DEPARTURE_THRESHOLD_MINUTES = 10;
    private static final String GATE_CHANGED_WARNING =
            "⚠️ <b>Гейт или терминал изменился.</b> Сверься с табло в аэропорту — оно точнее приложения.";

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
    @Column(name = "expected_departure")
    private String expectedDeparture;
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
        flight.setExpectedDeparture(dto.getExpectedDeparture());
        flight.setScheduledArrival(dto.getScheduledArrival());
        flight.setActualArrival(dto.getActualArrival());
        flight.setStatus(dto.getStatus());
        flight.setTerminal(dto.getTerminal());
        flight.setGate(dto.getGate());

        return flight;
    }

    /**
     * Есть ли изменения, о которых стоит уведомить. Сдвиг ожидаемого вылета меньше
     * EXPECTED_DEPARTURE_THRESHOLD_MINUTES не считается: такие сдвиги не сохраняем,
     * поэтому медленный дрейф по 2–3 минуты всё равно накопится и даст уведомление.
     */
    public boolean hasSignificantChanges(FlightDto dto) {
        if (dto == null) return false;

        return !equalsIgnoreCase(this.flightCode, dto.getFlight()) ||
                !equalsIgnoreCase(this.number, dto.getNumber()) ||
                !equalsIgnoreCase(this.airline, dto.getAirline()) ||
                !equalsIgnoreCase(this.departureAirport, dto.getDepartureAirport()) ||
                !equalsIgnoreCase(this.arrivalAirport, dto.getArrivalAirport()) ||
                !equalsIgnoreCase(this.scheduledDeparture, dto.getScheduledDeparture()) ||
                !equalsIgnoreCase(this.scheduledArrival, dto.getScheduledArrival()) ||
                !equalsIgnoreCase(this.status, dto.getStatus()) ||
                isGateChanged(dto) ||
                isExpectedDepartureShifted(dto);
    }

    /**
     * Запись создана подпиской (только номер рейса) и ещё ни разу не заполнялась.
     * Первое заполнение — не «изменение», уведомлять о нём не нужно.
     */
    public boolean isNotFilledYet() {
        return status == null && scheduledDeparture == null && departureAirport == null;
    }

    public boolean isGateChanged(FlightDto dto) {
        return !equalsIgnoreCase(this.terminal, dto.getTerminal())
                || !equalsIgnoreCase(this.gate, dto.getGate());
    }

    private boolean isExpectedDepartureShifted(FlightDto dto) {
        String before = expectedDeparture != null ? expectedDeparture : scheduledDeparture;
        String after = dto.getExpectedDeparture() != null ? dto.getExpectedDeparture() : dto.getScheduledDeparture();
        if (equalsIgnoreCase(before, after)) return false;
        Long shift = FlightTimes.getMinutesBetweenOrNull(before, after);
        return shift == null || Math.abs(shift) >= EXPECTED_DEPARTURE_THRESHOLD_MINUTES;
    }

    /**
     * Источник иногда не отдаёт гейт/терминал (сбой, другой сайт). Пустое значение
     * считаем «нет данных», а не «гейт сняли», чтобы не слать ложное уведомление.
     */
    public void fillMissingGateAndTerminal(FlightDto dto) {
        if (dto.getGate() == null) dto.setGate(gate);
        if (dto.getTerminal() == null) dto.setTerminal(terminal);
    }

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    public String getDifference(FlightDto dto) {
        String flight = getDifferenceString(flightCode + number, dto.getFlight() + dto.getNumber());
        StringBuilder sb = new StringBuilder();
        sb.append(FlightDto.getValueOrDash(dto.getAirline() != null ? dto.getAirline() : airline))
                .append(" (" + flight + ")").append("\n");

        sb.append(getDifferenceString(departureAirport, dto.getDepartureAirport()))
                .append(" -> ")
                .append(getDifferenceString(arrivalAirport, dto.getArrivalAirport()))
                .append("\n");

        sb.append("<b>DEP: </b>").append(getDifferenceString(
                FlightDto.getDepartureText(scheduledDeparture, expectedDeparture),
                FlightDto.getDepartureText(dto.getScheduledDeparture(), dto.getExpectedDeparture()))).append("\n");
        sb.append("<b>ARR: </b>").append(getDifferenceString(scheduledArrival, dto.getScheduledArrival())).append("\n");
        sb.append("<b>STATUS: </b>").append(getDifferenceString(status, dto.getStatus())).append("\n");
        sb.append("<b>TERMINAL: </b>").append(getDifferenceString(terminal, dto.getTerminal())).append(" ");
        sb.append("<b>GATE: </b>").append(getDifferenceString(gate, dto.getGate())).append("\n");

        if (isGateChanged(dto)) {
            sb.append("\n").append(GATE_CHANGED_WARNING).append("\n");
        }

        return sb.toString();
    }

    private String getDifferenceString(String s1, String s2) {
        // Гейт и терминал часто приходят пустыми — без null-safe сравнения тут был NPE
        String before = FlightDto.getValueOrDash(s1);
        String after = FlightDto.getValueOrDash(s2);
        return before.equalsIgnoreCase(after) ? before
                : before + " -> " + after
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
