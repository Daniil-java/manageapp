package com.kuklin.manageapp.common.entities;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_users")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class TelegramUser {
    @Id
    private Long telegramId;
    private String username;
    private String firstname;
    private String lastname;
    private String languageCode;
    @Enumerated(EnumType.STRING)
    private BotIdentifier botIdentifier;
    @UpdateTimestamp
    private LocalDateTime updated;
    @CreationTimestamp
    private LocalDateTime created;

    public static TelegramUser convertFromTelegram(User user) {
        return new TelegramUser()
                .setTelegramId(user.getId())
                .setUsername(user.getUserName())
                .setFirstname(user.getFirstName())
                .setLastname(user.getLastName())
                .setLanguageCode(user.getLanguageCode());
    }
}
