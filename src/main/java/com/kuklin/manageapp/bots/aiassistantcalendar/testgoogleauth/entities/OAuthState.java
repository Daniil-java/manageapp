package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class OAuthState {
    @Id
    @Column(name = "state", nullable = false)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "verifier", nullable = false, length = 256)
    private String verifier;

    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
