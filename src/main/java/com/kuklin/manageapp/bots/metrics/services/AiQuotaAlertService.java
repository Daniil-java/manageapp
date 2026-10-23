package com.kuklin.manageapp.bots.metrics.services;

import com.kuklin.manageapp.aiconversation.models.AiQuotaExhaustedEvent;
import com.kuklin.manageapp.bots.metrics.configurations.MetricsBotKeyComponents;
import com.kuklin.manageapp.bots.metrics.models.AiUsageByUser;
import com.kuklin.manageapp.bots.metrics.repositories.MetricsAiInteractionRecordRepository;
import com.kuklin.manageapp.bots.metrics.telegram.MetricsTelegramBot;
import com.kuklin.manageapp.common.configurations.EnabledBots;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Кончились кредиты у провайдера ИИ ({@link AiQuotaExhaustedEvent}) — алерт админам метрикс-бота
 * со статистикой: сколько вызовов за час и за сутки, кто больше всех тратит и нет ли подозрительных.
 * Пока счёт пуст, падает каждый вызов — алерт не чаще раза в 30 минут.
 * Статистика — только по успешным вызовам: упавшие в metrics_ai_interaction_record не пишутся.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiQuotaAlertService {

    static final Duration COOLDOWN = Duration.ofMinutes(30);
    // Обычный пользователь — 5–15 вызовов в день, лимит Premium — 150 в сутки
    static final long SUSPICIOUS_PER_HOUR = 30;
    // Один пользователь — больше половины всех вызовов за час, если вызовов набралось заметно
    static final long SHARE_MIN_TOTAL = 20;
    static final int SUSPICIOUS_SHARE_PERCENT = 50;
    static final int TOP_USERS = 5;

    private final MetricsAiInteractionRecordRepository repository;
    private final TelegramUserService telegramUserService;
    private final MetricsTelegramBot metricsTelegramBot;
    private final MetricsBotKeyComponents metricsBotKeyComponents;
    private final EnabledBots enabledBots;

    private final AtomicReference<Instant> lastSentAt = new AtomicReference<>();

    @EventListener
    public void onQuotaExhausted(AiQuotaExhaustedEvent event) {
        if (!enabledBots.isEnabled(BotIdentifier.METRICS)) return;
        if (!tryAcquire(Instant.now())) return;
        // Событие приходит в потоке запроса пользователя — запросы к БД и отправку туда не тянем
        CompletableFuture.runAsync(() -> sendAlert(event));
    }

    boolean tryAcquire(Instant now) {
        Instant last = lastSentAt.get();
        if (last != null && Duration.between(last, now).compareTo(COOLDOWN) < 0) return false;
        return lastSentAt.compareAndSet(last, now);
    }

    private void sendAlert(AiQuotaExhaustedEvent event) {
        String text;
        try {
            Instant now = Instant.now();
            List<AiUsageByUser> lastHour = repository.findUsageByUserSince(now.minus(Duration.ofHours(1)));
            List<AiUsageByUser> lastDay = repository.findUsageByUserSince(now.minus(Duration.ofDays(1)));
            text = buildAlertText(event, lastHour, lastDay, this::buildUserLabel);
        } catch (Exception e) {
            log.warn("Failed to build AI usage stats for quota alert", e);
            text = buildHeader(event) + "\n\nСтатистику собрать не удалось: " + escape(e.getMessage());
        }
        for (Long adminId : metricsBotKeyComponents.getAdminIds()) {
            metricsTelegramBot.sendReturnedMessage(adminId, text);
        }
    }

    // #42 @username, без юзернейма — telegram id; не из калорийного бота — только id
    private String buildUserLabel(Long appUserId) {
        String label = "#" + appUserId;
        return telegramUserService.findByAppUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT)
                .map(u -> label + (u.getUsername() != null ? " @" + u.getUsername() : " tg " + u.getTelegramId()))
                .orElse(label);
    }

    /**
     * lastHour — по убыванию числа вызовов (как отдаёт репозиторий), lastDay — в любом порядке.
     * userLabel — подпись пользователя по appUserId.
     */
    static String buildAlertText(AiQuotaExhaustedEvent event,
                                 List<AiUsageByUser> lastHour,
                                 List<AiUsageByUser> lastDay,
                                 Function<Long, String> userLabel) {
        StringBuilder sb = new StringBuilder(buildHeader(event));
        long hourTotal = lastHour.stream().mapToLong(AiUsageByUser::getRequestsOrZero).sum();

        sb.append("\n\n<b>За последний час:</b> ").append(buildTotals(lastHour));
        lastHour.stream()
                .limit(TOP_USERS)
                .forEach(u -> sb.append("\n• ").append(buildUserLine(u, userLabel)));

        sb.append("\n\n<b>За 24 часа:</b> ").append(buildTotals(lastDay));
        lastDay.stream()
                .sorted(Comparator.comparing(AiUsageByUser::getCostOrZero)
                        .thenComparingLong(AiUsageByUser::getRequestsOrZero)
                        .reversed())
                .limit(TOP_USERS)
                .forEach(u -> sb.append("\n• ").append(buildUserLine(u, userLabel)));

        List<String> suspicious = lastHour.stream()
                .filter(u -> u.appUserId() != null && isSuspicious(u, hourTotal))
                .map(u -> escape(userLabel.apply(u.appUserId())) + " — " + u.getRequestsOrZero()
                        + " за час (" + getSharePercent(u, hourTotal) + "% всех)")
                .toList();
        if (suspicious.isEmpty()) {
            sb.append("\n\n✅ Подозрительных расходов не видно");
        } else {
            sb.append("\n\n⚠️ <b>Подозрительно</b> (больше ").append(SUSPICIOUS_PER_HOUR)
                    .append(" вызовов за час или больше ").append(SUSPICIOUS_SHARE_PERCENT).append("% всех):");
            suspicious.forEach(s -> sb.append("\n• ").append(s));
        }
        return sb.toString();
    }

    static boolean isSuspicious(AiUsageByUser usage, long hourTotal) {
        long requests = usage.getRequestsOrZero();
        if (requests > SUSPICIOUS_PER_HOUR) return true;
        return hourTotal >= SHARE_MIN_TOTAL && getSharePercent(usage, hourTotal) > SUSPICIOUS_SHARE_PERCENT;
    }

    private static long getSharePercent(AiUsageByUser usage, long total) {
        return total == 0 ? 0 : usage.getRequestsOrZero() * 100 / total;
    }

    private static String buildHeader(AiQuotaExhaustedEvent event) {
        return "💸 <b>" + event.provider() + ": кончились кредиты</b>\n"
                + "Все вызовы ИИ падают, пока не пополнить счёт.\n"
                + "<code>" + escape(cut(event.details(), 300)) + "</code>\n"
                + "<i>Ниже — только успешные вызовы, упавшие не учитываются.</i>";
    }

    private static String buildTotals(List<AiUsageByUser> usage) {
        if (usage.isEmpty()) return "вызовов нет";
        long requests = usage.stream().mapToLong(AiUsageByUser::getRequestsOrZero).sum();
        long tokens = usage.stream().mapToLong(AiUsageByUser::getTokensOrZero).sum();
        BigDecimal cost = usage.stream().map(AiUsageByUser::getCostOrZero).reduce(BigDecimal.ZERO, BigDecimal::add);
        return requests + " выз., " + formatTokens(tokens) + " ток., " + formatCost(cost)
                + ", пользователей: " + usage.stream().filter(u -> u.appUserId() != null).count();
    }

    private static String buildUserLine(AiUsageByUser usage, Function<Long, String> userLabel) {
        String who = usage.appUserId() == null ? "без пользователя" : escape(userLabel.apply(usage.appUserId()));
        return who + " — " + usage.getRequestsOrZero() + " выз., " + formatTokens(usage.getTokensOrZero())
                + " ток., " + formatCost(usage.getCostOrZero());
    }

    static String formatTokens(long tokens) {
        if (tokens < 1000) return String.valueOf(tokens);
        return BigDecimal.valueOf(tokens).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString() + "k";
    }

    private static String formatCost(BigDecimal cost) {
        return "$" + cost.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
