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
    private String name;
    private Integer dailySlots;
    private Boolean isActive;

    public enum TopicType {
        SCIENCE_ARTICLE,
        ARTICLE,
        FACT,
        MYTH,
        TOP,
        QUIZ,
        QUIZ_ANSWER,
        CASE,
        MEME
    }
}
