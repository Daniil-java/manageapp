package com.kuklin.manageapp.bots.channelposter.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "channel_post_queue")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PostQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long categoryId;
    private Long topicId;
    private String textContent;
    private String imageDescription;
    private Instant scheduledAt;
    @Enumerated(EnumType.STRING)
    private PostQueueStatus status;
    private Instant sentAt;
    private Integer tgMessageId;
    private Integer parentPostId;
    private Long sourceArticleId;
    @CreationTimestamp
    private Instant created;
    public enum PostQueueStatus {
        TEXT_GENERATED, IMAGE_GENERATED, SENT, PENDING, QUEUED, FAILED

    }
}
