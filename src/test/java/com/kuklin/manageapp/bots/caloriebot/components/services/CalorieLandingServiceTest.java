package com.kuklin.manageapp.bots.caloriebot.components.services;

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
        service = new CalorieLandingService(pricingPlanService, planFeatureService, aiLimits);
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

        var page = service.getLandingPage();

        assertThat(page.plans()).extracting(CalorieLandingService.PlanCard::title).containsExactly("MONTH", "THREE");
        assertThat(page.plans().get(0).saving()).isNull();
        assertThat(page.plans().get(1).saving()).isEqualTo(22);
        assertThat(page.plans().get(1).price()).isEqualTo("350 ⭐");
        assertThat(page.plans().get(1).duration()).isEqualTo("3 месяца");
    }

    @Test
    void packagesAndPlansWithoutDurationAreHidden() {
        PricingPlan pack = plan("PACK", 30, 100, Currency.XTR)
                .setPayloadType(PricingPlan.PricingPlanType.GENERATION_REQUEST);
        PricingPlan noDuration = plan("BROKEN", 0, 100, Currency.XTR);
        when(pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(BOT))
                .thenReturn(List.of(pack, noDuration));

        assertThat(service.getLandingPage().plans()).isEmpty();
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

        var rows = service.getLandingPage().features();

        assertThat(rows.get(0).free()).isEqualTo("до 100 в сутки");
        assertThat(rows.get(0).premium()).isEqualTo("до 300 в сутки");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo(BotFeature.DISH_AI_VISION.getDisplayName());
            assertThat(row.free()).isEqualTo("2 в день");
            assertThat(row.premium()).isEqualTo("Без лимита");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo(BotFeature.REPORT_PDF_WEEK.getDisplayName());
            assertThat(row.free()).isEqualTo("—");
            assertThat(row.premium()).isEqualTo("—");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo(BotFeature.DISH_FAVORITE_LIST.getDisplayName());
            assertThat(row.free()).isEqualTo("до 5");
        });
    }

    @Test
    void rublesFormattedFromKopecks() {
        assertThat(CalorieLandingService.formatPrice(19900, Currency.RUB)).isEqualTo("199 ₽");
        assertThat(CalorieLandingService.formatPrice(19950, Currency.RUB)).isEqualTo("199,5 ₽");
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
        assertThat(service.getLandingPage().botUrl()).isEqualTo("https://t.me/calorydairy_bot");

        ReflectionTestUtils.setField(service, "startCode", "landing");
        assertThat(service.getLandingPage().botUrl()).isEqualTo("https://t.me/calorydairy_bot?start=landing");
    }
}
