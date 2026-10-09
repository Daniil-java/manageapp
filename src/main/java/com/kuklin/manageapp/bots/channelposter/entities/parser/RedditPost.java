package com.kuklin.manageapp.bots.channelposter.entities.parser;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * @deprecated Таблица reddit_post. Заменена на channel_source_item (SourceItem).
 * <p>Устарело в et-85. Причина: old.reddit.com без логина теперь редиректит на /login (reason=lor2) —
 * парсер получал страницу входа вместо ленты и находил 0 постов, а ошибки уходили только в warn-лог.
 * Поэтому в канал почти ничего не приходило. Вместо Reddit-only конвейера сделан общий:
 * ContentSource / SourceItem, парсеры RSS, PubMed и Reddit (официальный API) и ContentPipeline.
 * <p>Код не запускается по расписанию и не пополняет таблицы subreddit / reddit_post.
 * Оставлен для истории; удалить вместе с таблицами отдельной миграцией.
 */
@Deprecated(since = "et-85", forRemoval = true)
@Entity
@Table(name = "reddit_post")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RedditPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String redditId;     // t3_xxx

    private Long subredditId;

    private String url;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private ContentType contentType;

    private String author;
    private Integer score;
    private Integer commentsCount;

    private Instant postCreated; // время на реддите

    @CreationTimestamp
    private Instant parsedAt;

    @Enumerated(EnumType.STRING)
    private PostStatus status;

    public enum ContentType {
        TEXT, LINK, IMAGE, VIDEO
    }

    public enum PostStatus {
        NEW,
        APPROVED,
        REJECTED,
        PROCESSED,
        PRE_AI_PRECESSED,
        FAILED
    }
}
