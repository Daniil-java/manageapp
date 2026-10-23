package com.kuklin.manageapp.bots.channelposter.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "channel_topic_category")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class TopicCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private TopicType topicType;
    // подпись рубрики для админа (/q, превью)
    private String name;
    private Integer dailySlots;
    private Boolean isActive;
    // дописывается последней строкой поста, null — без хэштега
    private String hashtag;
    // посты рубрики автопилот ставит в очередь без оценки AI (у рубрик нет источника)
    private boolean autopilot;

    public enum TopicType {
        SCIENCE_ARTICLE,
        ARTICLE,
        FACT,
        MYTH,
        TOP,
        // квиз: опрос с правильным ответом и пояснением
        QUIZ,
        // пост с итогами квиза или опроса — ответом на него
        QUIZ_ANSWER,
        // опрос мнений, без правильного ответа
        POLL,
        // фото блюда + квиз «сколько калорий», ответ — распознавание калорийного бота
        GUESS_CALORIES,
        CASE,
        MEME
    }
}
