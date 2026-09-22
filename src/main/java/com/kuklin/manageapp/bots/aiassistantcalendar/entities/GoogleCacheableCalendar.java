package com.kuklin.manageapp.bots.aiassistantcalendar.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "user_google_calendar_cache")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class GoogleCacheableCalendar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;
    @Column(name = "calendar_id", nullable = false)
    private String calendarId;
    @Column(name = "summary", nullable = false)
    private String summary;
}
