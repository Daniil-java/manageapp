package com.kuklin.manageapp.bots.bookingbot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "states")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ConversationState {
    @Id
    private Long telegramId;
    @Enumerated(EnumType.STRING)
    private Step step; // текущий шаг диалога
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum Step {
        START,
        FILL_FORM,
        CONFIRMATION,
        COMPLETED
    }
}
