package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import org.springframework.stereotype.Component;

/**
 * Название и описание тарифа на языке пользователя — словарь i18n/calorie/plans_*,
 * ключ plan.<code_for_order_id>.title / .description. Нет ключа — title / description из БД.
 * Общему модулю оплаты (счёт в Telegram) эти тексты отдаёт PaymentModuleTexts.
 */
@Component
public class PlanTexts extends AbstractBotTexts {
    private static final String KEY_PREFIX = "plan.";

    public PlanTexts(CalorieTexts texts) {
        super(texts);
    }

    public String getTitle(PricingPlan plan, Long appUserId) {
        return getTitle(plan, languageOf(appUserId));
    }

    public String getDescription(PricingPlan plan, Long appUserId) {
        return getDescription(plan, languageOf(appUserId));
    }

    public String getTitle(PricingPlan plan, AppLanguage language) {
        String text = texts.findOrNull(language, buildTitleKey(plan.getCodeForOrderId()));
        return text != null ? text : plan.getTitle();
    }

    public String getDescription(PricingPlan plan, AppLanguage language) {
        String text = texts.findOrNull(language, buildDescriptionKey(plan.getCodeForOrderId()));
        return text != null ? text : plan.getDescription();
    }

    /**
     * Описание для счёта Telegram — только первая строка («Доступ ко всем функциям на 30 дней»):
     * переносы строк в описании счёта Telegram не показывает, пункты слились бы в одну строку.
     */
    public String getInvoiceDescription(PricingPlan plan, AppLanguage language) {
        String description = getDescription(plan, language);
        if (description == null) return null;
        return description.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse(description);
    }

    public static String buildTitleKey(String planCode) {
        return KEY_PREFIX + planCode + ".title";
    }

    public static String buildDescriptionKey(String planCode) {
        return KEY_PREFIX + planCode + ".description";
    }
}
