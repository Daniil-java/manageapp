package com.kuklin.manageapp.bots.channelposter.entities.source;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Материал, найденный в источнике: новость, абстракт исследования или пост с Reddit.
 * NEW → (AI-фильтр) → APPROVED / REJECTED → (генерация поста) → PROCESSED / FAILED
 */
@Entity
@Table(name = "channel_source_item")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class SourceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceId;

    // guid / ссылка / PMID / t3_xxx — уникален, по нему дедупликация
    private String externalId;

    private String url;
    private String title;

    // анонс из ленты — его видит AI-фильтр
    @Column(columnDefinition = "TEXT")
    private String summary;

    // полный текст, если пришёл в ленте (PubMed, Reddit, content:encoded)
    @Column(columnDefinition = "TEXT")
    private String content;

    private Instant publishedAt;
    private Integer score;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Integer aiScore;
    private String aiReason;
    private Long postQueueId;

    @CreationTimestamp
    private Instant parsedAt;

    public enum Status {
        NEW, APPROVED, REJECTED, PROCESSED, FAILED
    }
}
