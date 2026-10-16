package com.kuklin.manageapp.bots.channelposter.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Тема поста рубрики из банка: «Есть после шести — толстеть» (миф), «Желудок обновляет слизистую…» (факт).
 * Тему выбирает код, модель только пишет по ней пост — так темы не повторяются.
 */
@Entity
@Table(name = "channel_rubric_topic")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RubricTopic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long categoryId;
    // раздел (белок, сахар, сон…): посты рубрики чередуют разделы
    private String section;
    private String text;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Instant usedAt;
    private Long postQueueId;
    @CreationTimestamp
    private Instant created;

    public enum Status {
        // ждёт поста
        NEW,
        // пост по теме написан
        USED,
        // выключена админом (/topics off)
        DISABLED
    }
}
