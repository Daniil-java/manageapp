package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;

/**
 * Ссылки на страницы калорийного бота на сайте — для кнопок и текстов бота.
 * Страницы бота живут на zefir.fit: на kuklin.dev /calorie/** закрыт (DomainUrlFilter), старые ссылки оттуда
 * перенаправляются сюда же.
 */
public final class CalorieSiteLinks {
    public static final String SITE_URL = "https://zefir.fit";
    public static final String INSTRUCTION_URL = SITE_URL + "/calorie/instruction";
    public static final String UTM_FORM_URL = SITE_URL + "/calorie/utm-form";

    private static final String PRIVACY_URL = SITE_URL + "/calorie/privacy";
    private static final String TERMS_URL = SITE_URL + "/calorie/terms";

    private CalorieSiteLinks() {
    }

    /** Политика конфиденциальности на языке пользователя. */
    public static String buildPrivacyUrl(AppLanguage language) {
        return PRIVACY_URL + "?lang=" + language.getCode();
    }

    /** Пользовательское соглашение на языке пользователя. */
    public static String buildTermsUrl(AppLanguage language) {
        return TERMS_URL + "?lang=" + language.getCode();
    }
}
