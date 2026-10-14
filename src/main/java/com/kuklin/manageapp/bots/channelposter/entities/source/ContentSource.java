package com.kuklin.manageapp.bots.channelposter.entities.source;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Откуда бот берёт материалы для постов: RSS-лента, поисковый запрос PubMed или сабреддит.
 */
@Entity
@Table(name = "channel_content_source")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ContentSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private SourceType type;

    private String name;

    // RSS — ссылка на ленту, PUBMED — поисковый запрос, REDDIT — имя сабреддита
    private String address;

    private Boolean active;

    // сколько самых свежих материалов брать за один сбор
    private Integer maxItems;

    private Instant lastFetchedAt;
    private Instant lastSuccessAt;
    private String lastError;
    private Integer failCount;

    @CreationTimestamp
    private Instant created;

    public enum SourceType {
        RSS, PUBMED, REDDIT
    }
}
