package com.kuklin.manageapp.bots.channelposter.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * Опрос или квиз в канале. Пост для слота и проверки — PostQueue (postQueueId), здесь — содержимое и голоса.
 */
@Entity
@Table(name = "channel_poll")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ChannelPoll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long postQueueId;
    @Enumerated(EnumType.STRING)
    private Type type;
    private String question;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> options;
    // только у квиза: индекс правильного варианта и пояснение (Telegram показывает его после ответа)
    private Integer correctOptionId;
    private String explanation;
    private String tgPollId;
    private Integer tgMessageId;
    // голоса по вариантам — последнее состояние, присланное Telegram
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Integer> optionVotes;
    private int totalVoters;
    private Instant publishedAt;
    private Instant resultsDueAt;
    private Instant resultsSentAt;
    // null при отправленных итогах — итоги пропущены (мало голосов)
    private Long resultsPostId;

    public enum Type {
        QUIZ, REGULAR,
        // «Угадай калорийность»: квиз под фото блюда, правильный ответ посчитал калорийный бот
        CALORIES
    }

    // в Telegram — квиз с правильным ответом
    public boolean isQuiz() {
        return type == Type.QUIZ || type == Type.CALORIES;
    }
}
