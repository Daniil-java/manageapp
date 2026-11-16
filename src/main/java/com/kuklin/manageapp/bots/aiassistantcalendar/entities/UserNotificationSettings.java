package com.kuklin.manageapp.bots.aiassistantcalendar.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "user_notification_settings")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserNotificationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;
    @Column(name = "daily_enabled", nullable = false)
    private boolean dailyEnabled = false; // включено ли утреннее уведомление
    private LocalTime dailyTime; // локальное время уведомления (по utcOffsetHours)
    private Integer utcOffsetHours; // смещение от UTC (например, 3 = UTC+3)
    private LocalDate lastDailyNotified; // когда в последний раз отправили утреннее уведомление
}
