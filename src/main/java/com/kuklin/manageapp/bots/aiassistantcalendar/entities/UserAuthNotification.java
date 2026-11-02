package com.kuklin.manageapp.bots.aiassistantcalendar.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_auth_notification")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserAuthNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long telegramId;

    @Enumerated(EnumType.STRING)
    private Status status;
    private String text;
    // Когда отправить
    private LocalDateTime executeAt;

    public enum Status {
        PENDING, NOTIFICATED, ERROR;
    }
}
