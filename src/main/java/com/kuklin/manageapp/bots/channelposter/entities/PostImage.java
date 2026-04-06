package com.kuklin.manageapp.bots.channelposter.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "channel_post_image")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PostImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postQueueId;
    private String filePath;          // путь к файлу на диске

    @Enumerated(EnumType.STRING)
    private ImageSource source;       // AI_GENERATED / ADMIN_UPLOAD

    @Enumerated(EnumType.STRING)
    private ImageStatus status;

    @CreationTimestamp
    private Instant created;

    public enum ImageSource { AI_GENERATED, ADMIN_UPLOAD }

    public enum ImageStatus { GENERATING, READY, APPROVED, REJECTED }
}
