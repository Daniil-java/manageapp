package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.BonusGrantsRow;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.CodeClicksRow;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.PaymentsRow;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.SourceFunnelRow;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.TopReferrerRow;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmReportService.SourceStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UtmReportServiceTest {

    private final UtmReportRepository repository = mock(UtmReportRepository.class);
    private final ReferralRewardService referralRewardService = mock(ReferralRewardService.class);
    private final UtmReportService service = new UtmReportService(repository, referralRewardService);

    @BeforeEach
    void setUp() {
        when(referralRewardService.getActivationDays()).thenReturn(3);
        when(referralRewardService.getActivationWindowDays()).thenReturn(7);
        // Строки-моки собираются до when(): Mockito не даёт настраивать мок внутри thenReturn другого
        PaymentsRow payments = payments(2, 300);
        List<CodeClicksRow> clicks = List.of(
                clicks("ref7", 5), clicks("ref9", 3), clicks("blogger_oleg", 40), clicks("zch12", 7));
        List<SourceFunnelRow> funnels = List.of(
                funnel("ref7", 3, 3, 2, 1, 1, 150),
                funnel("ref9", 2, 2, 2, 1, 0, 0),
                funnel("blogger_oleg", 10, 9, 6, 2, 1, 150),
                funnel(null, 4, 3, 1, 0, 0, 0));
        when(repository.findPayments(any(), any())).thenReturn(payments);
        when(repository.findClicks(any(), any())).thenReturn(clicks);
        when(repository.findSourceFunnels(any(), any(), eq(3), eq(7))).thenReturn(funnels);
    }

    @Test
    void referralAndChannelCodesAreGrouped() {
        assertThat(UtmReportService.getGroupLabel("ref123")).isEqualTo("ref*");
        assertThat(UtmReportService.getGroupLabel("zch5")).isEqualTo("zch*");
        assertThat(UtmReportService.getGroupLabel(null)).isEqualTo("—");
        assertThat(UtmReportService.getGroupLabel("blogger_oleg")).isEqualTo("blogger_oleg");
    }

    @Test
    void codeFromCommandMatchesGroup() {
        assertThat(UtmReportService.getLabelForQuery("ref")).isEqualTo("ref*");
        assertThat(UtmReportService.getLabelForQuery("REF*")).isEqualTo("ref*");
        assertThat(UtmReportService.getLabelForQuery("zch")).isEqualTo("zch*");
        assertThat(UtmReportService.getLabelForQuery(" Blogger_Oleg ")).isEqualTo("blogger_oleg");
    }

    @Test
    void sourcesAreMergedWithClicksAndSortedByNewUsers() {
        List<SourceStats> sources = service.getSourceStats(Instant.EPOCH, Instant.now());

        assertThat(sources).extracting(SourceStats::label).containsExactly("blogger_oleg", "ref*", "—", "zch*");
        SourceStats ref = sources.get(1);
        assertThat(ref).isEqualTo(new SourceStats("ref*", 8, 5, 5, 4, 2, 1, 150));
        // Пост канала: переходы есть, новых нет — строка всё равно в отчёте
        assertThat(sources.get(3)).isEqualTo(new SourceStats("zch*", 7, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void reportShowsTotalsTableAndReferrals() {
        List<BonusGrantsRow> grants = List.of(
                bonus(ReferralRewardService.INVITED_PLAN_CODE, 5, 15),
                bonus(ReferralRewardService.INVITER_PLAN_CODE, 2, 14));
        List<TopReferrerRow> top = List.of(referrer(7L, "olga", 3, 1, 1));
        when(repository.findBonusGrants(any(), any())).thenReturn(grants);
        when(repository.findTopReferrers(any(), any(), anyInt())).thenReturn(top);

        String report = service.buildReport(30);

        assertThat(report)
                .contains("Источники за 30 дн.")
                .contains("Новых: <b>19</b> · по ссылкам 15 · сами 4")
                .contains("Оплат за период (все пользователи): 2 · 300 ⭐")
                .contains("<pre>")
                .contains("blogger_oleg")
                .contains("итого")
                .contains("Приглашённых: 5 · ведут дневник 2 · оплатили 1 (150 ⭐)")
                .contains("Выдано Premium: <b>29 дн.</b>")
                .contains("другу за первое блюдо: 5 раз, 15 дн.")
                .contains("@olga (id 7): пришли 3 · активны 1 · оплатили 1");
        verify(repository).findTopReferrers(any(), any(), eq(UtmReportService.TOP_REFERRERS));
    }

    @Test
    void tableRowsAreAligned() {
        List<SourceStats> sources = List.of(new SourceStats("a_very_long_source_code", 1, 2, 3, 4, 5, 6, 7));

        String table = UtmReportService.buildTable(sources, new SourceStats("итого", 1, 2, 3, 4, 5, 6, 7));

        List<String> lines = table.lines().toList();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).startsWith("a_very_long_s…");
        assertThat(lines.get(1).length()).isEqualTo(lines.get(2).length());
    }

    @Test
    void codeReportShowsFunnelWithPercents() {
        String report = service.buildCodeReport("blogger_oleg", 7);

        assertThat(report)
                .contains("<b>blogger_oleg</b> за 7 дн.")
                .contains("Переходов: 40")
                .contains("Новых: <b>10</b>")
                .contains("Выбрали язык: 9 (90%)")
                .contains("Первое блюдо: 6 (60%)")
                .contains("Блюда в 3+ дня за первую неделю: 2 (20%)")
                .contains("Оплатили: 1 (10%) · 150 ⭐");
    }

    @Test
    void unknownCodeReportsNoData() {
        assertThat(service.buildCodeReport("nobody", 30)).contains("Ни переходов, ни новых");
    }

    @Test
    void percentOfZeroIsDash() {
        assertThat(UtmReportService.getPercent(3, 0)).isEqualTo("—");
        assertThat(UtmReportService.getPercent(1, 3)).isEqualTo("33%");
    }

    @Test
    void dailyLineSplitsNewUsersBySource() {
        String line = service.buildDailyLine(LocalDate.of(2026, 10, 20), ZoneId.of("Asia/Ho_Chi_Minh"));

        assertThat(line).isEqualTo("📈 Калорийный бот: новых 19 (по ссылкам 15, сами 4) · оплат 2 · 300 ⭐");
    }

    private static SourceFunnelRow funnel(String code, long newUsers, long language, long dish, long active,
                                          long paid, long stars) {
        SourceFunnelRow row = mock(SourceFunnelRow.class);
        when(row.getCode()).thenReturn(code);
        when(row.getNewUsers()).thenReturn(newUsers);
        when(row.getLanguageChosen()).thenReturn(language);
        when(row.getFirstDish()).thenReturn(dish);
        when(row.getActive()).thenReturn(active);
        when(row.getPaid()).thenReturn(paid);
        when(row.getStars()).thenReturn(stars);
        return row;
    }

    private static CodeClicksRow clicks(String code, long clicks) {
        CodeClicksRow row = mock(CodeClicksRow.class);
        when(row.getCode()).thenReturn(code);
        when(row.getClicks()).thenReturn(clicks);
        return row;
    }

    private static PaymentsRow payments(long count, long stars) {
        PaymentsRow row = mock(PaymentsRow.class);
        when(row.getPayments()).thenReturn(count);
        when(row.getStars()).thenReturn(stars);
        return row;
    }

    private static BonusGrantsRow bonus(String planCode, long grants, long days) {
        BonusGrantsRow row = mock(BonusGrantsRow.class);
        when(row.getPlanCode()).thenReturn(planCode);
        when(row.getGrants()).thenReturn(grants);
        when(row.getDays()).thenReturn(days);
        return row;
    }

    private static TopReferrerRow referrer(Long id, String username, long invited, long activated, long paid) {
        TopReferrerRow row = mock(TopReferrerRow.class);
        when(row.getReferrerAppUserId()).thenReturn(id);
        when(row.getUsername()).thenReturn(username);
        when(row.getInvited()).thenReturn(invited);
        when(row.getActivated()).thenReturn(activated);
        when(row.getPaid()).thenReturn(paid);
        return row;
    }
}
