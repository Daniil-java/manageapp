package com.kuklin.manageapp.bots.caloriebot.i18n;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Языки калорийного бота. Значения — application.yaml, блок calorie.i18n.
 */
@Data
@Component
@ConfigurationProperties(prefix = "calorie.i18n")
public class I18nProperties {
    /** Язык тех, кто язык не выбирал, и запасной словарь, если в нужном нет ключа. */
    private AppLanguage defaultLanguage = AppLanguage.RU;
    /** Telegram id тех, кому доступна команда /lang (пользователям выбор языка пока не открыт). */
    private List<Long> devTelegramIds = List.of();
}
