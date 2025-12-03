package com.kuklin.manageapp.payment.entities;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Баланс генераций пользователя в рамках конкретного бота.
 *
 * Хранит:
 * - telegramId пользователя;
 * - текущее количество доступных запросов (generationRequests);
 * - BotIdentifier, к которому относится этот баланс.
 */
@Entity
@Table(name = "generation_balance")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class GenerationBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;
    private Long generationRequests;
    @Enumerated(EnumType.STRING)
    private BotIdentifier botIdentifier;

    // Уменьшает текущий баланс на request и возвращает обновлённую сущность.
    public GenerationBalance subtract(Long request) {
        generationRequests = generationRequests - request;
        return this;
    }

    // Увеличивает текущий баланс на request и возвращает обновлённую сущность.
    public GenerationBalance topUp(Long request) {
        generationRequests = generationRequests + request;
        return this;
    }

}
