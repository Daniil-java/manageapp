package com.kuklin.manageapp.bots.channelposter.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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

    // Время выхода поста каждый день (например, 10:00)
    private LocalTime postTime;

    private Boolean isActive = true;
}
