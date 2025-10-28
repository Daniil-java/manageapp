package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;

@Entity
@Table(name = "assistant_google_oauth")
@Getter @Setter @Accessors(chain = true)
public class AssistantGoogleOAuth {
    @Id
    private Long telegramId;            // ключ = твой Telegram chat/user id
    private String googleSub;           // при желании заполнить запросом userinfo
    @Column(length = 4096)
    private String refreshTokenEnc;     // TODO: шифровать
    @Column(length = 2048)
    private String accessToken;
    private Instant accessExpiresAt;
    @Column(length = 1024)
    private String scope;
}
