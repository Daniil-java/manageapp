package com.kuklin.manageapp.bots.caloriebot.i18n;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Языки калорийного бота. Значения — application.yaml, блок calorie.i18n.
 */
@Data
@Component
@ConfigurationProperties(prefix = "calorie.i18n")
// Проверка при старте: значение вне диапазона или пропало из application.yaml — приложение не запустится
@Validated
public class I18nProperties {
    /** Язык тех, кто язык не выбирал, и запасной словарь, если в нужном нет ключа. */
    @NotNull
    private AppLanguage defaultLanguage = AppLanguage.RU;
    /** Telegram id тех, кому доступна команда /lang (пользователям выбор языка пока не открыт). */
    @NotNull
    private List<Long> devTelegramIds = List.of();
}
