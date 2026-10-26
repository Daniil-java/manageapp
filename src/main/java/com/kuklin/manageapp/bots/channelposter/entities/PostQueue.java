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
    private String title;
    private String textContent;
    private String imageDescription;
    // ключевые слова на английском для поиска фото на Pixabay (AI пишет вместе с постом): точные и запасные,
    // пошире — их фото идут в выдаче после точных
    private String imageQuery;
    private String imageQueryBroad;
    // id фото Pixabay у поста (null — фото не со стока): «🔁 Другое фото» берёт следующее после него,
    // а фото других постов, в том числе вышедших, повторно не берутся
    private Long stockPhotoId;
    private Instant scheduledAt;
    @Enumerated(EnumType.STRING)
    private PostQueueStatus status;
    private Instant sentAt;
    private Integer tgMessageId;
    private Integer parentPostId;
    // откуда материал (null — статья прислана админом вручную)
    private Long sourceItemId;
    private String sourceUrl;
    private String sourceName;
    // оценка AI-фильтра 0–10: по ней автопилот решает, ставить ли пост в очередь без админа
    private Integer aiScore;
    // когда превью ушло админам на проверку
    private Instant reviewSentAt;
    // реакции на пост в канале — последнее число от Telegram (апдейт message_reaction_count)
    private int reactions;
    private Instant reactionsUpdatedAt;
    @CreationTimestamp
    private Instant created;
    public enum PostQueueStatus {
        TEXT_GENERATED, IMAGE_GENERATED, SENT, PENDING, QUEUED, FAILED, PROCESSED,
        // текст и картинка готовы, превью у админов — ждёт «в очередь» / «опубликовать» / «удалить»
        REVIEW,
        // висел на проверке дольше channelposter.review-ttl — снят с конвейера (не занимает место),
        // но кнопки превью работают: админ может поставить его в очередь и позже
        EXPIRED

    }
}
