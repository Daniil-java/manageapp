package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.BonusGrantsRow;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.PaymentsRow;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.SourceFunnelRow;
import com.kuklin.manageapp.bots.caloriebot.components.repository.UtmReportRepository.TopReferrerRow;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportHtml;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Отчёт по источникам калорийного бота: откуда приходят новые пользователи, кто из них ведёт дневник и платит.
 * Для админов (/utmstats, вечерний отчёт метрик), поэтому тексты здесь, по-русски, а не в словарях бота.
 * <p>
 * Воронка — по когорте: пришедшие за период и что они сделали к сегодняшнему дню. Источник — первая UTM-ссылка
 * (utm_attributions); реферальные ссылки ref&lt;id&gt; собраны в одну строку «ref*», посты канала zch&lt;id&gt; — в «zch*»,
 * пришедшие без ссылки — «—».
 */
@Service
@RequiredArgsConstructor
public class UtmReportService {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 365;
    static final int TOP_REFERRERS = 5;
    static final String REFERRAL_GROUP = UtmService.REFERRAL_CODE_PREFIX + "*";
    static final String CHANNEL_POST_GROUP = UtmService.CHANNEL_POST_CODE_PREFIX + "*";
    static final String NO_SOURCE = "—";
    private static final int LABEL_WIDTH = 14;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM");
    private static final Map<String, String> BONUS_LABELS = Map.of(
            ReferralRewardService.INVITED_PLAN_CODE, "другу за первое блюдо",
            ReferralRewardService.INVITER_PLAN_CODE, "за активного друга",
            ReferralRewardService.FRIEND_PAID_PLAN_CODE, "за оплату друга");

    private final UtmReportRepository utmReportRepository;
    private final ReferralRewardService referralRewardService;

    /** Источник: переходы и воронка пришедших за период. */
    public record SourceStats(String label, long clicks, long newUsers, long languageChosen, long firstDish,
                              long active, long paid, long stars) {

        SourceStats plus(SourceStats other) {
            return new SourceStats(label, clicks + other.clicks, newUsers + other.newUsers,
                    languageChosen + other.languageChosen, firstDish + other.firstDish, active + other.active,
                    paid + other.paid, stars + other.stars);
        }
    }

    /** Сводка за последние days дней: все источники, рефералка, топ пригласивших. */
    @Transactional(readOnly = true)
    public String buildReport(int days) {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(days));
        List<SourceStats> sources = getSourceStats(from, to);
        SourceStats total = getTotal(sources);
        long organic = sources.stream().filter(s -> s.label().equals(NO_SOURCE)).mapToLong(SourceStats::newUsers).sum();
        PaymentsRow payments = utmReportRepository.findPayments(from, to);

        StringBuilder sb = new StringBuilder()
                .append("📊 <b>Источники за ").append(days).append(" дн.</b> (")
                .append(formatDay(from)).append("–").append(formatDay(to)).append(", UTC)\n")
                .append("Пришли за период и что сделали к сегодняшнему дню.\n\n")
                .append("Новых: <b>").append(total.newUsers()).append("</b> · по ссылкам ")
                .append(total.newUsers() - organic).append(" · сами ").append(organic).append('\n')
                .append("Оплат за период (все пользователи): ").append(payments.getPayments())
                .append(" · ").append(payments.getStars()).append(" ⭐\n\n");

        if (sources.isEmpty()) {
            sb.append("Новых и переходов за период нет.\n");
        } else {
            sb.append("<pre>").append(buildTable(sources, total)).append("</pre>\n")
                    .append("3дн — блюда в ").append(referralRewardService.getActivationDays())
                    .append(" разных дня за первые ").append(referralRewardService.getActivationWindowDays())
                    .append(" дней. ").append(REFERRAL_GROUP).append(" — друзья, ").append(CHANNEL_POST_GROUP)
                    .append(" — посты канала, ").append(NO_SOURCE).append(" — без ссылки.\n")
                    .append("Подробно: /utmstats код [дни]\n");
        }

        sb.append('\n').append(buildReferralSection(from, to, sources));
        return sb.toString();
    }

    /**
     * Воронка одного источника за последние days дней. code — код ссылки, «ref» / «zch» (с «*» или без) — вся группа.
     */
    @Transactional(readOnly = true)
    public String buildCodeReport(String code, int days) {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(days));
        String label = getLabelForQuery(code);
        SourceStats stats = getSourceStats(from, to).stream()
                .filter(s -> s.label().equals(label))
                .findFirst()
                .orElse(null);
        String title = "📊 <b>" + ReportHtml.escapeHtml(label) + "</b> за " + days + " дн.\n";
        if (stats == null) {
            return title + "Ни переходов, ни новых за период.";
        }
        long base = stats.newUsers();
        return title
                + "Переходов: " + stats.clicks() + '\n'
                + "Новых: <b>" + base + "</b>\n"
                + "Выбрали язык: " + stats.languageChosen() + " (" + getPercent(stats.languageChosen(), base) + ")\n"
                + "Первое блюдо: " + stats.firstDish() + " (" + getPercent(stats.firstDish(), base) + ")\n"
                + "Блюда в " + referralRewardService.getActivationDays() + "+ дня за первую неделю: "
                + stats.active() + " (" + getPercent(stats.active(), base) + ")\n"
                + "Оплатили: " + stats.paid() + " (" + getPercent(stats.paid(), base) + ") · " + stats.stars() + " ⭐";
    }

    /** Строка для вечернего отчёта метрик: новые за день по источникам и оплаты дня. */
    @Transactional(readOnly = true)
    public String buildDailyLine(LocalDate day, ZoneId zone) {
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        List<SourceStats> sources = getSourceStats(from, to);
        long newUsers = sources.stream().mapToLong(SourceStats::newUsers).sum();
        long organic = sources.stream().filter(s -> s.label().equals(NO_SOURCE)).mapToLong(SourceStats::newUsers).sum();
        PaymentsRow payments = utmReportRepository.findPayments(from, to);
        return "📈 Калорийный бот: новых " + newUsers + " (по ссылкам " + (newUsers - organic) + ", сами " + organic
                + ") · оплат " + payments.getPayments() + " · " + payments.getStars() + " ⭐";
    }

    /** Источники за [from, to): ref* и zch* собраны в группы, сортировка — по новым, затем по переходам. */
    List<SourceStats> getSourceStats(Instant from, Instant to) {
        Map<String, SourceStats> byLabel = new LinkedHashMap<>();
        int activeDays = referralRewardService.getActivationDays();
        int windowDays = referralRewardService.getActivationWindowDays();
        for (SourceFunnelRow row : utmReportRepository.findSourceFunnels(from, to, activeDays, windowDays)) {
            String label = getGroupLabel(row.getCode());
            byLabel.merge(label, new SourceStats(label, 0, row.getNewUsers(), row.getLanguageChosen(),
                    row.getFirstDish(), row.getActive(), row.getPaid(), row.getStars()), SourceStats::plus);
        }
        for (UtmReportRepository.CodeClicksRow row : utmReportRepository.findClicks(from, to)) {
            String label = getGroupLabel(row.getCode());
            byLabel.merge(label, new SourceStats(label, row.getClicks(), 0, 0, 0, 0, 0, 0), SourceStats::plus);
        }
        List<SourceStats> sources = new ArrayList<>(byLabel.values());
        sources.sort(Comparator.comparingLong(SourceStats::newUsers).reversed()
                .thenComparing(Comparator.comparingLong(SourceStats::clicks).reversed()));
        return sources;
    }

    /** Строка таблицы для кода: реферальные и посты канала — группой, без кода — «—». */
    static String getGroupLabel(String code) {
        if (code == null) {
            return NO_SOURCE;
        }
        if (code.startsWith(UtmService.REFERRAL_CODE_PREFIX)) {
            return REFERRAL_GROUP;
        }
        if (code.startsWith(UtmService.CHANNEL_POST_CODE_PREFIX)) {
            return CHANNEL_POST_GROUP;
        }
        return code;
    }

    /** Код из команды → строка таблицы: «ref», «ref*», «ref123» — группа друзей; так же «zch». */
    static String getLabelForQuery(String code) {
        String normalized = code.trim().toLowerCase();
        if (normalized.endsWith("*")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return getGroupLabel(normalized);
    }

    private static SourceStats getTotal(List<SourceStats> sources) {
        return sources.stream().reduce(new SourceStats("итого", 0, 0, 0, 0, 0, 0, 0), SourceStats::plus);
    }

    /** Моноширинная таблица для &lt;pre&gt;: метка обрезана до LABEL_WIDTH символов. */
    static String buildTable(List<SourceStats> sources, SourceStats total) {
        StringBuilder sb = new StringBuilder(String.format("%-" + LABEL_WIDTH + "s %5s %4s %4s %4s %3s %3s %5s%n",
                "источник", "клик", "нов", "язык", "блюд", "3дн", "опл", "⭐"));
        List<SourceStats> rows = new ArrayList<>(sources);
        rows.add(total);
        for (SourceStats s : rows) {
            String label = s.label().length() > LABEL_WIDTH ? s.label().substring(0, LABEL_WIDTH - 1) + "…" : s.label();
            sb.append(String.format("%-" + LABEL_WIDTH + "s %5d %4d %4d %4d %3d %3d %5d%n",
                    ReportHtml.escapeHtml(label), s.clicks(), s.newUsers(), s.languageChosen(), s.firstDish(),
                    s.active(), s.paid(), s.stars()));
        }
        return sb.toString();
    }

    private String buildReferralSection(Instant from, Instant to, List<SourceStats> sources) {
        StringBuilder sb = new StringBuilder("🎁 <b>Рефералка</b>\n");
        sources.stream().filter(s -> s.label().equals(REFERRAL_GROUP)).findFirst().ifPresentOrElse(
                ref -> sb.append("Приглашённых: ").append(ref.newUsers())
                        .append(" · ведут дневник ").append(ref.active())
                        .append(" · оплатили ").append(ref.paid()).append(" (").append(ref.stars()).append(" ⭐)\n"),
                () -> sb.append("Приглашённых за период нет.\n"));

        List<BonusGrantsRow> grants = utmReportRepository.findBonusGrants(from, to);
        if (grants.isEmpty()) {
            sb.append("Бонусных дней не выдано.\n");
        } else {
            long totalDays = grants.stream().mapToLong(BonusGrantsRow::getDays).sum();
            sb.append("Выдано Premium: <b>").append(totalDays).append(" дн.</b>\n");
            for (BonusGrantsRow grant : grants) {
                sb.append("• ").append(BONUS_LABELS.getOrDefault(grant.getPlanCode(), grant.getPlanCode()))
                        .append(": ").append(grant.getGrants()).append(" раз, ").append(grant.getDays()).append(" дн.\n");
            }
        }

        List<TopReferrerRow> top = utmReportRepository.findTopReferrers(from, to, TOP_REFERRERS);
        if (!top.isEmpty()) {
            sb.append("Топ пригласивших:\n");
            for (TopReferrerRow row : top) {
                String name = row.getUsername() != null ? "@" + ReportHtml.escapeHtml(row.getUsername()) : "без username";
                sb.append("• ").append(name).append(" (id ").append(row.getReferrerAppUserId()).append("): пришли ")
                        .append(row.getInvited()).append(" · активны ").append(row.getActivated())
                        .append(" · оплатили ").append(row.getPaid()).append('\n');
            }
        }
        return sb.toString();
    }

    /** Доля part от total в процентах, «—» — если total = 0. */
    static String getPercent(long part, long total) {
        return total == 0 ? NO_SOURCE : Math.round(part * 100.0 / total) + "%";
    }

    private static String formatDay(Instant instant) {
        return DAY_FORMAT.format(instant.atZone(ZoneOffset.UTC));
    }
}
