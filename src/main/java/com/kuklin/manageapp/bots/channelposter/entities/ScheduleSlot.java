package com.kuklin.manageapp.bots.channelposter.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "channel_schedule_slot")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ScheduleSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ссылка на категорию (тип поста), например ARTICLE или MEME
    private Long topicCategoryId;

    // Время выхода поста (например, 10:00)
    private LocalTime postTime;

    // День недели ISO: 1 — пн … 7 — вс; null — каждый день
    private Integer dayOfWeek;

    private Boolean isActive = true;

    public boolean isOn(LocalDate date) {
        return dayOfWeek == null || dayOfWeek == date.getDayOfWeek().getValue();
    }
}
