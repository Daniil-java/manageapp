package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Родитель классов, которые собирают тексты бота из словаря (профиль, карточка блюда, настройки…).
 * Здесь — общий доступ к словарю и одинаковые для всех кусочки: число с единицей, длительность.
 * <p>
 * Числа в шаблоны передаём строками: ICU иначе поставил бы разделители разрядов (2 000 мл).
 * Числом — только когда от него зависит склонение («21 год», «3 days»).
 */
public abstract class AbstractBotTexts {
    protected final CalorieTexts texts;

    protected AbstractBotTexts(CalorieTexts texts) {
        this.texts = texts;
    }

    protected AppLanguage languageOf(Long appUserId) {
        return texts.languageOf(appUserId);
    }

    /** «175 см», «80.5 кг» или noData, если значения нет. */
    protected String getWithUnit(Number value, String unitKey, AppLanguage language, String noData) {
        if (value == null) return noData;
        String number = value instanceof BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString()
                : String.valueOf(value);
        return number + " " + texts.get(language, unitKey);
    }

    /** «3 ч», «2 мин», «90 сек»: самой крупной единицей, в которую длительность делится нацело. */
    protected String getDurationText(Duration duration, AppLanguage language) {
        long seconds = duration.toSeconds();
        if (seconds > 0 && seconds % 3600 == 0) {
            return texts.get(language, BotTextKeys.COMMON_DURATION_HOURS, String.valueOf(seconds / 3600));
        }
        if (seconds % 60 == 0) {
            return texts.get(language, BotTextKeys.COMMON_DURATION_MINUTES, String.valueOf(seconds / 60));
        }
        return texts.get(language, BotTextKeys.COMMON_DURATION_SECONDS, String.valueOf(seconds));
    }
}
