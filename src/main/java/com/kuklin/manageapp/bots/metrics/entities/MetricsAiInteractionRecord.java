package com.kuklin.manageapp.bots.metrics.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kuklin.manageapp.aiconversation.models.enums.AiOperation;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
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
    // Часть inputTokens, взятая из кэша OpenAI (в 10 раз дешевле)
    private Long cachedInputTokens;
    private String model;
    // Чей вызов и зачем — из AiUsageContext; null у старых записей и у вызовов без контекста
    private Long appUserId;
    @Enumerated(EnumType.STRING)
    private AiOperation operation;
    // null — модели нет в AiModelPrice
    private BigDecimal costUsd;
    @CreationTimestamp
    private Instant created;

    public enum AiMessageType {
        TEXT, VOICE, PHOTO, IMAGE;
    }

}
