package com.kuklin.manageapp.bots.aiassistantcalendar.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_messages_log")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserMessagesLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;
    private String username;
    private String firstname;
    private String lastname;
    private String googleEmail;
    private String message;

    @CreationTimestamp
    private LocalDateTime createdAt; // дата/время создания обращения
}
