package com.kuklin.manageapp.bots.aiassistantcalendar.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "assistant_google_oauth")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AssistantGoogleOAuth {
    @Id
    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "google_sub")
    private String googleSub;

    @Column(name = "email")
    private String email;

    @Column(name = "refresh_token_enc")
    private String refreshTokenEnc; // AES-GCM

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "scope")
    private String scope;

    @Column(name = "default_calendar_id")
    private String defaultCalendarId;

    @Column(name = "last_refresh_at")
    private Instant lastRefreshAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
