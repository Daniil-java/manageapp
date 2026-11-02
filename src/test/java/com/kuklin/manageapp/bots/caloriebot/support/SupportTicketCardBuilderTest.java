package com.kuklin.manageapp.bots.caloriebot.support;

import com.kuklin.manageapp.bots.caloriebot.components.repository.SupportMessageRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.SupportTicketRepository;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.configurations.SupportProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.SupportMessage;
import com.kuklin.manageapp.bots.caloriebot.entities.SupportTicket;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.metrics.repositories.MetricsAiInteractionRecordRepository;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.repositories.PaymentRepository;
import com.kuklin.manageapp.payment.repositories.PricingPlanRepository;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupportTicketCardBuilderTest {
    private static final long USER = 7L;

    private final SupportTicketRepository ticketRepository = mock(SupportTicketRepository.class);
    private final SupportMessageRepository messageRepository = mock(SupportMessageRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final UserSubscriptionService subscriptionService = mock(UserSubscriptionService.class);
    private final PricingPlanRepository planRepository = mock(PricingPlanRepository.class);
    private final TelegramUser user = new TelegramUser();
    private SupportTicketCardBuilder builder;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(USER)).thenReturn("ru");
        SupportProperties properties = new SupportProperties();
        properties.setChatId(-1001234567890L);
        properties.setZone(ZoneId.of("UTC"));
        builder = new SupportTicketCardBuilder(properties, ticketRepository, messageRepository,
                subscriptionService, planRepository, paymentRepository,
                mock(MetricsAiInteractionRecordRepository.class), new CalorieTexts(new I18nProperties(), settings));

        user.setAppUserId(USER);
        user.setTelegramId(555L);
        user.setFirstname("Аня");
        user.setUsername("anya");
    }

    private static SupportTicket buildTicket(long id, SupportTicket.Category category) {
        return new SupportTicket().setId(id).setAppUserId(USER).setCategory(category)
                .setStatus(SupportTicket.Status.OPEN).setCreatedAt(Instant.parse("2026-10-12T09:00:00Z"));
    }

    @Test
    void firstTicketSaysFirstContact() {
        String card = builder.buildCard(buildTicket(12, SupportTicket.Category.BUG), user);

        assertThat(card)
                .startsWith("🐞 <b>Ошибка в боте</b> · заявка #12")
                .contains("<b>Аня</b></a> @anya 🇷🇺")
                .contains("💎 Без подписки")
                .contains("💳 Платежей не было")
                .contains("🤖 ИИ за сутки: 0")
                .contains("🗂 Первое обращение")
                .contains("<blockquote expandable>🔧 tg <code>555</code> · appUserId <code>7</code>");
    }

    @Test
    void previousTicketsAreListedWithLinkRatingAndPreview() {
        SupportTicket old = buildTicket(5, SupportTicket.Category.PAYMENT)
                .setStatus(SupportTicket.Status.CLOSED).setTopicId(40).setRating((short) 1);
        when(ticketRepository.findTop5ByAppUserIdAndStatusNotAndIdNotOrderByCreatedAtDesc(USER, SupportTicket.Status.DRAFT, 12L))
                .thenReturn(List.of(old));
        when(ticketRepository.countByAppUserIdAndStatusNot(USER, SupportTicket.Status.DRAFT)).thenReturn(2L);
        when(messageRepository.findFirstByTicketIdAndDirectionOrderByCreatedAtAsc(5L, SupportMessage.Direction.USER))
                .thenReturn(Optional.of(new SupportMessage().setText("Списали звёзды, а подписки нет")));

        String card = builder.buildCard(buildTicket(12, SupportTicket.Category.BUG), user);

        assertThat(card)
                .contains("🗂 <b>Прошлые заявки</b> (1)")
                .contains("✅👍 <a href=\"https://t.me/c/1234567890/40\">#5</a> 💳 12.10.26 — Списали звёзды, а подписки нет");
    }

    @Test
    void starsPaymentIsShortOnTopAndChargeIdIsInTechBlock() {
        Payment payment = new Payment();
        payment.setStarsAmount(299);
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        payment.setProviderPaymentId("stxCharge1");
        payment.setPaidAt(Instant.parse("2026-11-01T10:00:00Z"));
        when(paymentRepository.findTop3ByAppUserIdAndBotIdentifierAndStatusInOrderByCreatedAtDesc(eq(USER), any(), any()))
                .thenReturn(List.of(payment));

        String card = builder.buildCard(buildTicket(12, SupportTicket.Category.PAYMENT), user);

        String top = card.substring(0, card.indexOf("<blockquote"));
        String tech = card.substring(card.indexOf("<blockquote"));
        assertThat(top).contains("💳 299 ⭐ 01.11.26 ✓").doesNotContain("stxCharge1");
        assertThat(tech)
                .contains("299 ⭐ 01.11.26: <code>stxCharge1</code>")
                .contains("<code>/refund stxCharge1</code>");
    }

    @Test
    void subscriptionShowsPlanTitleAndDaysLeft() {
        UserSubscription subscription = new UserSubscription();
        subscription.setPricingPlanId(3L);
        subscription.setEndAt(Instant.now().plus(Duration.ofDays(12)).plus(Duration.ofHours(1)));
        when(subscriptionService.getActiveSubscriptionOrNull(eq(USER), any())).thenReturn(subscription);
        PricingPlan plan = new PricingPlan();
        plan.setTitle("Premium Месяц");
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan));

        String card = builder.buildCard(buildTicket(12, SupportTicket.Category.PAYMENT), user);

        assertThat(card).contains("💎 Premium Месяц — ещё 12 дн.");
    }

    @Test
    void topicNameHasNumberCategoryAndUser() {
        assertThat(builder.buildTopicName(buildTicket(12, SupportTicket.Category.BUG), user)).isEqualTo("#12 🐞 Аня @anya");
    }
}
