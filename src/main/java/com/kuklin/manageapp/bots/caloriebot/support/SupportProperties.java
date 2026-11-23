package com.kuklin.manageapp.bots.caloriebot.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Поддержка калорийного бота: заявки из /support уходят темами в группу поддержки.
 * Значения — application.yaml, блок calorie.support.
 */
@Data
@Component
@ConfigurationProperties(prefix = "calorie.support")
// Проверка при старте: значение вне диапазона или пропало из application.yaml — приложение не запустится
@Validated
public class SupportProperties {
    /** id группы-форума поддержки (-100…). null — заявок нет, /support показывает ссылку на аккаунт поддержки. */
    private Long chatId;
    /** Напомнить в теме, если на сообщение пользователя столько времени нет ответа. */
    @NotNull
    private Duration remindAfter;
    /** Столько ждём описание (или ответ) после кнопки; дальше сообщение снова считается едой. */
    @NotNull
    private Duration inputTimeout;
    /** Часовой пояс дат в группе поддержки. */
    @NotNull
    private ZoneId zone;

    public boolean isEnabled() {
        return chatId != null;
    }
}
