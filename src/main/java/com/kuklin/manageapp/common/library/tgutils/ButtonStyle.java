package com.kuklin.manageapp.common.library.tgutils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Цвет кнопки (Bot API 9.4, поле style). Других цветов в Telegram нет; без стиля — обычная серая кнопка.
 * Старые клиенты стиль не знают и показывают серую.
 */
@Getter
@RequiredArgsConstructor
public enum ButtonStyle {
    /** Синий — главный призыв к действию. */
    PRIMARY("primary"),
    /** Зелёный — подтверждение: сохранить, да, оплатить. */
    SUCCESS("success"),
    /** Красный — удаление и отмена без возврата. */
    DANGER("danger");

    /** Значение поля style в Bot API. */
    private final String apiValue;
}
