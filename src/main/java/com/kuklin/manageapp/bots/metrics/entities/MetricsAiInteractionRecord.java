package com.kuklin.manageapp.bots.metrics.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "metrics_ai_interaction_record")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class MetricsAiInteractionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ProviderVariant providerVariant;
    @Enumerated(EnumType.STRING)
    private BotIdentifier botIdentifier;
    private String request;
    @Enumerated(EnumType.STRING)
    private AiMessageType requestMessageType;
    private String response;
    @Enumerated(EnumType.STRING)
    private AiMessageType responseMessageType;
    private Long inputTokens;
    private Long outputTokens;
    @CreationTimestamp
    private Instant created;

    public enum AiMessageType {
        TEXT, VOICE, PHOTO, IMAGE;
    }

}
