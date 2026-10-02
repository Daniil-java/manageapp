package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.components.repository.CalorieAiInsightRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.CalorieAiInsight;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.report.AiInsightDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalorieAiInsightServiceTest {

    private static final Long USER_ID = 42L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String SUMMARY_JSON = "{\"headline\":\"Solid week\",\"bullets\":[]}";

    private CalorieAiInsightRepository repository;
    private ReportService reportService;
    private CalorieAiInsightService service;

    @BeforeEach
    void setUp() {
        repository = mock(CalorieAiInsightRepository.class);
        reportService = mock(ReportService.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        when(userSettingsService.getOrCreate(USER_ID)).thenReturn(new UserSettings().setTimezoneId(ZONE.getId()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new CalorieAiInsightService(repository, reportService, userSettingsService, new ObjectMapper());
    }

    // ── Чтение ─────────────────────────────────────────────

    @Test
    void getLatestReturnsEmptyWhenNeverGenerated() {
        when(repository.findByAppUserIdAndType(USER_ID, AiInsightType.WEEKLY_SUMMARY)).thenReturn(Optional.empty());

        assertThat(service.getLatest(USER_ID, AiInsightType.WEEKLY_SUMMARY)).isEmpty();
    }

    @Test
    void getLatestAllReturnsEveryStoredTypeWithParsedPayload() {
        when(repository.findAllByAppUserId(USER_ID)).thenReturn(List.of(
                insight(AiInsightType.WEEKLY_SUMMARY, Instant.now()),
                insight(AiInsightType.PATTERNS_MONTH, Instant.now())
        ));

        Map<AiInsightType, AiInsightDto> all = service.getLatestAll(USER_ID);

        assertThat(all).containsOnlyKeys(AiInsightType.WEEKLY_SUMMARY, AiInsightType.PATTERNS_MONTH);
        assertThat(all.get(AiInsightType.WEEKLY_SUMMARY).payload().get("headline").asText()).isEqualTo("Solid week");
    }

    @Test
    void weeklyInsightIsStaleFromNextDay() {
        Instant yesterday = LocalDate.now(ZONE).minusDays(1).atTime(12, 0).atZone(ZONE).toInstant();
        when(repository.findAllByAppUserId(USER_ID)).thenReturn(List.of(
                insight(AiInsightType.WEEKLY_SUMMARY, Instant.now()),
                insight(AiInsightType.PATTERNS_WEEK, yesterday)
        ));

        Map<AiInsightType, AiInsightDto> all = service.getLatestAll(USER_ID);

        assertThat(all.get(AiInsightType.WEEKLY_SUMMARY).stale()).isFalse();
        assertThat(all.get(AiInsightType.PATTERNS_WEEK).stale()).isTrue();
    }

    @Test
    void monthlyInsightIsStaleAfterSevenDays() {
        Instant sixDaysAgo = Instant.now().minus(Duration.ofDays(6));
        Instant eightDaysAgo = Instant.now().minus(Duration.ofDays(8));
        when(repository.findAllByAppUserId(USER_ID)).thenReturn(List.of(
                insight(AiInsightType.MONTHLY_SUMMARY, sixDaysAgo),
                insight(AiInsightType.PATTERNS_MONTH, eightDaysAgo)
        ));

        Map<AiInsightType, AiInsightDto> all = service.getLatestAll(USER_ID);

        assertThat(all.get(AiInsightType.MONTHLY_SUMMARY).stale()).isFalse();
        assertThat(all.get(AiInsightType.PATTERNS_MONTH).stale()).isTrue();
    }

    // ── Обновление ─────────────────────────────────────────

    @Test
    void refreshAsksAiForLastSevenDaysInUserZoneAndSaves() {
        when(repository.findByAppUserIdAndType(USER_ID, AiInsightType.WEEKLY_SUMMARY)).thenReturn(Optional.empty());
        when(reportService.getAiInsightReport(eq(AiInsightType.WEEKLY_SUMMARY), any(), any(), eq(USER_ID)))
                .thenReturn(AccessResult.success(SUMMARY_JSON));

        AiInsightDto dto = service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY);

        LocalDate today = LocalDate.now(ZONE);
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(reportService).getAiInsightReport(eq(AiInsightType.WEEKLY_SUMMARY), from.capture(), to.capture(), eq(USER_ID));
        assertThat(from.getValue()).isEqualTo(today.minusDays(6).atStartOfDay(ZONE).toInstant());
        assertThat(to.getValue()).isBefore(today.plusDays(1).atStartOfDay(ZONE).toInstant());
        assertThat(to.getValue().atZone(ZONE).toLocalDate()).isEqualTo(today);

        assertThat(dto.periodFrom()).isEqualTo(today.minusDays(6));
        assertThat(dto.periodTo()).isEqualTo(today);
        assertThat(dto.stale()).isFalse();
        assertThat(dto.payload().get("headline").asText()).isEqualTo("Solid week");

        ArgumentCaptor<CalorieAiInsight> saved = ArgumentCaptor.forClass(CalorieAiInsight.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAppUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getPayload()).isEqualTo(SUMMARY_JSON);
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void refreshMonthUsesThirtyDays() {
        when(repository.findByAppUserIdAndType(USER_ID, AiInsightType.MONTHLY_SUMMARY)).thenReturn(Optional.empty());
        when(reportService.getAiInsightReport(any(), any(), any(), any())).thenReturn(AccessResult.success(SUMMARY_JSON));

        AiInsightDto dto = service.refresh(USER_ID, AiInsightType.MONTHLY_SUMMARY);

        assertThat(dto.periodFrom()).isEqualTo(LocalDate.now(ZONE).minusDays(29));
    }

    @Test
    void refreshOverwritesExistingRowInsteadOfAddingNew() {
        CalorieAiInsight existing = insight(AiInsightType.WEEKLY_SUMMARY, Instant.now().minus(Duration.ofDays(3)));
        existing.setId(7L).setPayload("{\"headline\":\"Old\"}");
        when(repository.findByAppUserIdAndType(USER_ID, AiInsightType.WEEKLY_SUMMARY)).thenReturn(Optional.of(existing));
        when(reportService.getAiInsightReport(any(), any(), any(), any())).thenReturn(AccessResult.success(SUMMARY_JSON));

        service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY);

        ArgumentCaptor<CalorieAiInsight> saved = ArgumentCaptor.forClass(CalorieAiInsight.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(saved.getValue().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getPayload()).isEqualTo(SUMMARY_JSON);
        assertThat(saved.getValue().getCreatedAt()).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
    }

    @Test
    void secondRefreshWithinMinuteIsRejected() {
        when(repository.findByAppUserIdAndType(any(), any())).thenReturn(Optional.empty());
        when(reportService.getAiInsightReport(any(), any(), any(), any())).thenReturn(AccessResult.success(SUMMARY_JSON));

        service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY);

        assertError(() -> service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY), ErrorStatus.AI_INSIGHT_TOO_FREQUENT);
        verify(reportService, times(1)).getAiInsightReport(any(), any(), any(), any());
    }

    @Test
    void cooldownIsPerType() {
        when(repository.findByAppUserIdAndType(any(), any())).thenReturn(Optional.empty());
        when(reportService.getAiInsightReport(any(), any(), any(), any())).thenReturn(AccessResult.success(SUMMARY_JSON));

        service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY);
        service.refresh(USER_ID, AiInsightType.PATTERNS_WEEK);

        verify(reportService, times(2)).getAiInsightReport(any(), any(), any(), any());
    }

    @Test
    void failedAiCallCountsTowardsCooldown() {
        when(reportService.getAiInsightReport(any(), any(), any(), any())).thenReturn(AccessResult.success(null));

        assertError(() -> service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY), ErrorStatus.AI_INSIGHT_FAILED);
        assertError(() -> service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY), ErrorStatus.AI_INSIGHT_TOO_FREQUENT);

        verify(reportService, times(1)).getAiInsightReport(any(), any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void notEnoughDataDoesNotStartCooldown() {
        when(reportService.getAiInsightReport(any(), any(), any(), any()))
                .thenThrow(new ErrorResponseException(ErrorStatus.AI_INSIGHT_NOT_ENOUGH_DATA));

        assertError(() -> service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY), ErrorStatus.AI_INSIGHT_NOT_ENOUGH_DATA);
        assertError(() -> service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY), ErrorStatus.AI_INSIGHT_NOT_ENOUGH_DATA);

        verify(reportService, times(2)).getAiInsightReport(any(), any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void parallelRefreshOfSameTypeIsRejectedWhileFirstRuns() throws Exception {
        CountDownLatch aiStarted = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);
        when(repository.findByAppUserIdAndType(any(), any())).thenReturn(Optional.empty());
        when(reportService.getAiInsightReport(any(), any(), any(), any())).thenAnswer(inv -> {
            aiStarted.countDown();
            releaseAi.await(5, TimeUnit.SECONDS);
            return AccessResult.success(SUMMARY_JSON);
        });

        CompletableFuture<AiInsightDto> first =
                CompletableFuture.supplyAsync(() -> service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY));
        assertThat(aiStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertError(() -> service.refresh(USER_ID, AiInsightType.WEEKLY_SUMMARY), ErrorStatus.AI_INSIGHT_TOO_FREQUENT);

        releaseAi.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).payload().get("headline").asText()).isEqualTo("Solid week");
        verify(reportService, times(1)).getAiInsightReport(any(), any(), any(), any());
    }

    // ── Helpers ────────────────────────────────────────────

    private static CalorieAiInsight insight(AiInsightType type, Instant createdAt) {
        return new CalorieAiInsight()
                .setAppUserId(USER_ID)
                .setType(type)
                .setPeriodFrom(LocalDate.now(ZONE).minusDays(type.getPeriodDays() - 1L))
                .setPeriodTo(LocalDate.now(ZONE))
                .setPayload(SUMMARY_JSON)
                .setCreatedAt(createdAt);
    }

    private static void assertError(Runnable call, ErrorStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ErrorResponseException.class)
                .extracting(e -> ((ErrorResponseException) e).getErrorStatus())
                .isEqualTo(expected);
    }
}
