package com.kuklin.manageapp.bots.bookingbot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "rules")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AvailabilityRule {
    /*
          Как работает
        - Если задан dayOfWeek → правило повторяется каждую неделю.
        - Если задана specificDate → это исключение (например, праздничный день).
        - При генерации календаря и клавиатуры времени система смотрит:
        - есть ли правило для конкретной даты → применяет его;
        - иначе берёт правило по дню недели.

     */
    public static final Integer DEFAULT_INTERVAL = 30;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // К какому объекту относится правило
    private Long bookingObjectId;

    // День недели (если правило повторяющееся)
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    // Конкретная дата (если это исключение или спец-день)
    private LocalDate specificDate;

    // Рабочее время
    private LocalTime startTime;   // с какого времени доступно
    private LocalTime endTime;     // до какого времени доступно

    // Шаг слота (например, 30 минут)
    private Integer slotDurationMinutes;

    private boolean working; // true = рабочий день, false = выходной

    public static AvailabilityRule defaultRule(Long bookingObjectId, DayOfWeek dayOfWeek) {
        return new AvailabilityRule()
                .setBookingObjectId(bookingObjectId)
                .setDayOfWeek(dayOfWeek)
                .setStartTime(LocalTime.of(9, 0))
                .setEndTime(LocalTime.of(18, 0))
                .setSlotDurationMinutes(DEFAULT_INTERVAL)
                .setWorking(true);
    }


}
