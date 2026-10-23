package com.kuklin.manageapp.bots.metrics.services;

import com.kuklin.manageapp.aiconversation.models.AiQuotaExhaustedEvent;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.bots.metrics.models.AiUsageByUser;
import com.kuklin.manageapp.common.configurations.EnabledBots;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiQuotaAlertServiceTest {

    private static final AiQuotaExhaustedEvent EVENT =
            new AiQuotaExhaustedEvent(ProviderVariant.OPENAI, "HTTP 429 insufficient_quota");

    @Test
    void flagsUserWithTooManyRequestsPerHour() {
        List<AiUsageByUser> hour = List.of(
                usage(1L, 45, 120_000, "0.30"),
                usage(2L, 5, 9_000, "0.02"),
                usage(null, 3, 1_500, null));

        String text = AiQuotaAlertService.buildAlertText(EVENT, hour, hour, id -> "#" + id);

        assertThat(text)
                .contains("кончились кредиты")
                .contains("53 выз., 130.5k ток., $0.32, пользователей: 2")
                .contains("без пользователя — 3 выз.")
                .contains("Подозрительно")
                .contains("#1 — 45 за час (84% всех)")
                .doesNotContain("#2 — 5 за час");
    }

    @Test
    void flagsUserWithLargeShareOfRequests() {
        List<AiUsageByUser> hour = List.of(usage(7L, 18, 40_000, "0.10"), usage(8L, 4, 8_000, "0.01"));

        String text = AiQuotaAlertService.buildAlertText(EVENT, hour, hour, id -> "#" + id);

        assertThat(text).contains("#7 — 18 за час (81% всех)");
    }

    @Test
    void quietHourHasNoSuspicious() {
        List<AiUsageByUser> hour = List.of(usage(1L, 6, 10_000, "0.02"), usage(2L, 5, 9_000, "0.02"));

        String text = AiQuotaAlertService.buildAlertText(EVENT, hour, List.of(), id -> "#" + id);

        assertThat(text)
                .contains("Подозрительных расходов не видно")
                .contains("<b>За 24 часа:</b> вызовов нет");
    }

    @Test
    void dayTopIsSortedByCost() {
        List<AiUsageByUser> day = List.of(usage(1L, 100, 50_000, "0.10"), usage(2L, 20, 200_000, "0.90"));

        String text = AiQuotaAlertService.buildAlertText(EVENT, List.of(), day, id -> "#" + id);

        assertThat(text.indexOf("#2 — 20 выз.")).isLessThan(text.indexOf("#1 — 100 выз."));
    }

    @Test
    void alertIsSentNotMoreOftenThanCooldown() {
        AiQuotaAlertService service = new AiQuotaAlertService(null, null, null, null, mock(EnabledBots.class));
        Instant start = Instant.parse("2026-10-23T10:00:00Z");

        assertThat(service.tryAcquire(start)).isTrue();
        assertThat(service.tryAcquire(start.plus(AiQuotaAlertService.COOLDOWN).minusSeconds(1))).isFalse();
        assertThat(service.tryAcquire(start.plus(AiQuotaAlertService.COOLDOWN))).isTrue();
    }

    @Test
    void formatsTokens() {
        assertThat(AiQuotaAlertService.formatTokens(999)).isEqualTo("999");
        assertThat(AiQuotaAlertService.formatTokens(12_000)).isEqualTo("12k");
        assertThat(AiQuotaAlertService.formatTokens(1_250_000)).isEqualTo("1250k");
    }

    private static AiUsageByUser usage(Long appUserId, long requests, long tokens, String cost) {
        return new AiUsageByUser(appUserId, requests, tokens, cost == null ? null : new BigDecimal(cost));
    }
}
