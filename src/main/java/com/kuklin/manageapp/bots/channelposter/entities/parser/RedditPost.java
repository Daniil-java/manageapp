package com.kuklin.manageapp.bots.channelposter.entities.parser;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

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
