package com.kuklin.manageapp.bots.aiassistantcalendar.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Entity
@Table(name = "notified_event")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class NotifiedEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_id", nullable = false)
    private String calendarId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt = Instant.now();
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

}
