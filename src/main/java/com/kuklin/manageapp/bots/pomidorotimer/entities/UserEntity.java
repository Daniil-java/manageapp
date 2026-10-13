package com.kuklin.manageapp.bots.pomidorotimer.entities;

import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pomidoro_users")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String password;

    private Long telegramId;
    private Long chatId;
    @Enumerated(EnumType.STRING)
    private BotState botState;
    private String firstname;
    private String lastname;
    private String languageCode;
    private Long lastUpdatedTaskId;
    private Long lastUpdatedTaskMessageId;
    private Long lastUpdatedTimerSettingsMessageId;
    @CreationTimestamp
    private LocalDateTime created;
}
