package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlanTextsTest {
    // Ограничения Telegram для счёта (sendInvoice / createInvoiceLink)
    private static final int INVOICE_TITLE_MAX = 32;
    private static final int INVOICE_DESCRIPTION_MAX = 255;

    private final PlanTexts planTexts =
            new PlanTexts(new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class)));

    private static PricingPlan plan(String code) {
        return new PricingPlan()
                .setCodeForOrderId(code)
                .setTitle("Title from DB")
                .setDescription("Description from DB");
    }

    @Test
    void textsComeFromDictionaryByPlanCode() {
        PricingPlan month = plan("CALORIE_PREMIUM_MONTH");

        assertThat(planTexts.getTitle(month, AppLanguage.RU)).isEqualTo("Premium Месяц");
        assertThat(planTexts.getTitle(month, AppLanguage.EN)).isEqualTo("Premium 1 Month");
        assertThat(planTexts.getDescription(month, AppLanguage.EN))
                .startsWith("Access to all features for 30 days\n\n🚀 ");
    }

    /** Telegram не показывает переносы в описании счёта — в счёт идёт только первая строка. */
    @Test
    void invoiceDescriptionIsFirstLineOnly() {
        assertThat(planTexts.getInvoiceDescription(plan("CALORIE_SUB_30_XTR"), AppLanguage.EN))
                .isEqualTo("30 days of access");
        assertThat(planTexts.getInvoiceDescription(plan("SOME_NEW_PLAN"), AppLanguage.RU))
                .isEqualTo("Description from DB");
    }

    @Test
    void planWithoutDictionaryKeysShowsDbTexts() {
        PricingPlan unknown = plan("SOME_NEW_PLAN");

        assertThat(planTexts.getTitle(unknown, AppLanguage.EN)).isEqualTo("Title from DB");
        assertThat(planTexts.getDescription(unknown, AppLanguage.EN)).isEqualTo("Description from DB");
        assertThat(planTexts.getTitle(plan(null), AppLanguage.RU)).isEqualTo("Title from DB");
    }

    @Test
    void everyPlanHasTitleAndDescriptionWithinInvoiceLimits() throws IOException {
        for (AppLanguage language : AppLanguage.values()) {
            Set<String> codes = getPlanCodes(language);
            assertThat(codes).as("Тарифы в plans_%s", language.getCode()).isNotEmpty();

            for (String code : codes) {
                String title = planTexts.getTitle(plan(code), language);
                String description = planTexts.getDescription(plan(code), language);
                assertThat(title).as("%s %s: title", code, language).isNotEqualTo("Title from DB")
                        .hasSizeLessThanOrEqualTo(INVOICE_TITLE_MAX);
                assertThat(description).as("%s %s: description", code, language).isNotEqualTo("Description from DB")
                        .hasSizeLessThanOrEqualTo(INVOICE_DESCRIPTION_MAX);
            }
        }
    }

    /** Коды тарифов из ключей словаря: plan.<code>.title и plan.<code>.description. */
    private static Set<String> getPlanCodes(AppLanguage language) throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("i18n/calorie/plans_" + language.getCode() + ".properties"));
        Set<String> codes = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            int lastDot = key.lastIndexOf('.');
            codes.add(key.substring("plan.".length(), lastDot));
        }
        return codes;
    }
}
