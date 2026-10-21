package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.texts.PlanTexts;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiRateLimitProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalorieLandingServiceTest {

    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;

    private PricingPlanService pricingPlanService;
    private PlanFeatureService planFeatureService;
    private CalorieLandingService service;

    @BeforeEach
    void setUp() {
        pricingPlanService = mock(PricingPlanService.class);
        planFeatureService = mock(PlanFeatureService.class);
        AiRateLimitProperties aiLimits = new AiRateLimitProperties();
        aiLimits.setPerDay(100);
        aiLimits.setPerDayPremium(300);
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class));
        service = new CalorieLandingService(pricingPlanService, planFeatureService, aiLimits, new PlanTexts(texts), texts);
    }

    private static PricingPlan plan(String code, int days, int price, Currency currency) {
        return new PricingPlan()
                .setTitle(code)
                .setCodeForOrderId(code)
                .setDurationDays(days)
                .setPriceMinor(price)
                .setCurrency(currency)
                .setPayloadType(PricingPlan.PricingPlanType.SUBSCRIPTION);
    }

    private static PlanFeature feature(BotFeature feature, FeatureLimitPeriod period, Integer limit) {
        return new PlanFeature().setFeature(feature).setLimitPeriod(period).setLimitValue(limit);
    }

    @Test
    void plansSortedByDurationWithSavingAgainstShortestPlan() {
        when(pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(BOT)).thenReturn(List.of(
                plan("THREE", 90, 350, Currency.XTR),
                plan("MONTH", 30, 150, Currency.XTR)));

        var page = service.getLandingPage(AppLanguage.RU);

        assertThat(page.plans()).extracting(CalorieLandingService.PlanCard::title).containsExactly("MONTH", "THREE");
        assertThat(page.plans().get(0).saving()).isNull();
        assertThat(page.plans().get(1).saving()).isEqualTo(22);
        assertThat(page.plans().get(1).price()).isEqualTo("350 ⭐");
        assertThat(page.plans().get(1).duration()).isEqualTo("3 месяца");
    }

    @Test
    void descriptionSplitsIntoPointsWithLeadingEmoji() {
        var points = CalorieLandingService.toPoints("""
                ✅ Без лимитов на фото
                - 📄 PDF-отчёты за неделю

                👩🏽‍💻 ИИ-разбор дня
                Доступ ко всем функциям
                """);

        assertThat(points).containsExactly(
                new CalorieLandingService.PlanPoint("✅", "Без лимитов на фото"),
                new CalorieLandingService.PlanPoint("📄", "PDF-отчёты за неделю"),
                new CalorieLandingService.PlanPoint("👩🏽‍💻", "ИИ-разбор дня"),
                new CalorieLandingService.PlanPoint(null, "Доступ ко всем функциям"));
        assertThat(CalorieLandingService.toPoints(null)).isEmpty();
    }

    @Test
    void packagesAndPlansWithoutDurationAreHidden() {
        PricingPlan pack = plan("PACK", 30, 100, Currency.XTR)
                .setPayloadType(PricingPlan.PricingPlanType.GENERATION_REQUEST);
        PricingPlan noDuration = plan("BROKEN", 0, 100, Currency.XTR);
        when(pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(BOT))
                .thenReturn(List.of(pack, noDuration));

        assertThat(service.getLandingPage(AppLanguage.RU).plans()).isEmpty();
    }

    @Test
    void featureRowsComeFromFreeAndFirstPaidPlan() {
        when(pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(BOT))
                .thenReturn(List.of(plan("MONTH", 30, 150, Currency.XTR)));
        when(planFeatureService.getFeaturesByPlanCode("FREE", BOT)).thenReturn(List.of(
                feature(BotFeature.DISH_AI_VISION, FeatureLimitPeriod.DAILY, 2),
                feature(BotFeature.REPORT_PDF_WEEK, FeatureLimitPeriod.LIFETIME, 0),
                feature(BotFeature.DISH_FAVORITE_LIST, FeatureLimitPeriod.LIFETIME, 5)));
        when(planFeatureService.getFeaturesByPlanCode("MONTH", BOT)).thenReturn(List.of(
                feature(BotFeature.DISH_AI_VISION, FeatureLimitPeriod.UNLIMITED, -1)));

        var rows = service.getLandingPage(AppLanguage.RU).features();

        assertThat(rows.get(0).free()).isEqualTo("до 100 в сутки");
        assertThat(rows.get(0).premium()).isEqualTo("до 300 в сутки");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Распознавание блюд по фото");
            assertThat(row.free()).isEqualTo("2 в день");
            assertThat(row.premium()).isEqualTo("Без лимита");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Недельный PDF-отчёт");
            assertThat(row.free()).isEqualTo("—");
            assertThat(row.premium()).isEqualTo("—");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Сохранённые блюда");
            assertThat(row.free()).isEqualTo("до 5");
        });
    }

    @Test
    void englishPageHasEnglishLimitsAndDurations() {
        when(pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(BOT)).thenReturn(List.of(
                plan("MONTH", 30, 150, Currency.XTR),
                plan("THREE", 90, 350, Currency.XTR),
                plan("WEEK_AND_HALF", 45, 100, Currency.RUB)));
        when(planFeatureService.getFeaturesByPlanCode("FREE", BOT)).thenReturn(List.of(
                feature(BotFeature.DISH_AI_VISION, FeatureLimitPeriod.DAILY, 2)));

        var page = service.getLandingPage(AppLanguage.EN);

        assertThat(page.plans()).extracting(CalorieLandingService.PlanCard::duration)
                .containsExactly("1 month", "45 days", "3 months");
        assertThat(page.features().get(0).name()).isEqualTo("AI requests: photo, text, voice, reports");
        assertThat(page.features().get(0).free()).isEqualTo("up to 100 a day");
        assertThat(page.features()).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Dish recognition from photos");
            assertThat(row.free()).isEqualTo("2 a day");
            assertThat(row.premium()).isEqualTo("—");
        });
    }

    @Test
    void russianDurationsUsePlurals() {
        assertThat(service.describeDuration(30, AppLanguage.RU)).isEqualTo("1 месяц");
        assertThat(service.describeDuration(90, AppLanguage.RU)).isEqualTo("3 месяца");
        assertThat(service.describeDuration(180, AppLanguage.RU)).isEqualTo("6 месяцев");
        assertThat(service.describeDuration(2, AppLanguage.RU)).isEqualTo("2 дня");
        assertThat(service.describeDuration(45, AppLanguage.RU)).isEqualTo("45 дней");
    }

    @Test
    void rublesFormattedFromKopecks() {
        assertThat(CalorieLandingService.formatPrice(19900, Currency.RUB, AppLanguage.RU)).isEqualTo("199 ₽");
        assertThat(CalorieLandingService.formatPrice(19950, Currency.RUB, AppLanguage.RU)).isEqualTo("199,5 ₽");
        assertThat(CalorieLandingService.formatPrice(19950, Currency.RUB, AppLanguage.EN)).isEqualTo("199.5 ₽");
    }

    @Test
    void landingIsOpenWhenAccessKeyNotSet() {
        assertThat(service.isClosed()).isFalse();
        assertThat(service.isAccessKeyValid("anything")).isFalse();

        ReflectionTestUtils.setField(service, "accessKey", "  ");
        assertThat(service.isClosed()).isFalse();
    }

    @Test
    void closedLandingAcceptsOnlyExactKey() {
        ReflectionTestUtils.setField(service, "accessKey", "s3cret");

        assertThat(service.isClosed()).isTrue();
        assertThat(service.isAccessKeyValid("s3cret")).isTrue();
        assertThat(service.isAccessKeyValid("s3cre")).isFalse();
        assertThat(service.isAccessKeyValid("S3CRET")).isFalse();
        assertThat(service.isAccessKeyValid(null)).isFalse();
    }

    @Test
    void botUrlGetsStartCodeOnlyWhenConfigured() {
        assertThat(service.getLandingPage(AppLanguage.RU).botUrl()).isEqualTo("https://t.me/calorydairy_bot");

        ReflectionTestUtils.setField(service, "startCode", "landing");
        assertThat(service.getLandingPage(AppLanguage.RU).botUrl()).isEqualTo("https://t.me/calorydairy_bot?start=landing");
    }

    @Test
    void campaignCodeOverridesConfiguredCode() {
        ReflectionTestUtils.setField(service, "startCode", "landing");

        assertThat(service.getLandingPage(AppLanguage.RU, "vk_ads").botUrl())
                .isEqualTo("https://t.me/calorydairy_bot?start=vk_ads");
        assertThat(service.getLandingPage(AppLanguage.RU, null).botUrl())
                .isEqualTo("https://t.me/calorydairy_bot?start=landing");
    }
}
