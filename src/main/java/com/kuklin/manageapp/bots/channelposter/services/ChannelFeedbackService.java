package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelDailyStats;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.repositories.ChannelDailyStatsRepository;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionCountUpdated;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionCount;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ReviewPosterUpdateHandler.CHANNEL_ZONE;

/**
 * Обратная связь канала: реакции на посты, подписчики по дням, переходы из постов в калорийного бота.
 * Без зависимости от бота канала (он сам получает этот сервис) — число подписчиков передаёт вызывающий.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelFeedbackService {

    /** Ссылка на бота — в каждом CTA_EVERY-м посте (статьи и рубрики с текстом). */
    public static final int CTA_EVERY = 3;
    private static final int TOP_POSTS = 3;

    private final PostQueueService postQueueService;
    private final ChannelDailyStatsRepository statsRepository;
    private final UtmService utmService;
    private final ChannelPosterBotKeyComponent keys;

    /**
     * Новое число реакций на сообщение канала. Сообщения других чатов и не наши посты пропускаем.
     */
    public void updateReactions(MessageReactionCountUpdated update) {
        if (update.getChat() == null || !keys.getChannelId().equals(update.getChat().getId())) return;
        int total = update.getReactions() == null ? 0 : update.getReactions().stream()
                .map(ReactionCount::getTotalCount)
                .mapToInt(c -> c == null ? 0 : c)
                .sum();
        postQueueService.findSentByTgMessageId(update.getMessageId()).ifPresent(post ->
                postQueueService.save(post.setReactions(total).setReactionsUpdatedAt(Instant.now())));
    }

    /** Ссылка на калорийного бота с UTM-кодом поста: по ней видно, сколько людей пришло из канала. */
    public String getBotLink(Long postId) {
        String code = utmService.getOrCreateChannelPostCode(postId);
        return "https://t.me/" + BotIdentifier.CALORIE_BOT.getBotUsername().replace("@", "") + "?start=" + code;
    }

    /** Строка в конце поста со ссылкой на калорийного бота. */
    public static String buildBotLinkLine(String botLink) {
        return "📸 Калории своей тарелки по фото — <a href=\"" + botLink.replace("&", "&amp;").replace("\"", "&quot;")
                + "\">" + BotIdentifier.CALORIE_BOT.getBotUsername() + "</a>";
    }

    /**
     * Блок утреннего отчёта: подписчики с приростом, переходы в бота, лучшие посты недели.
     *
     * @param subscribersNow подписчиков сейчас (null — Telegram не ответил)
     * @param saveSnapshot   запомнить число на сегодня (утренний отчёт; /src report — нет)
     */
    public String buildReportSection(Integer subscribersNow, boolean saveSnapshot) {
        LocalDate today = LocalDate.now(CHANNEL_ZONE);
        StringBuilder sb = new StringBuilder();

        if (subscribersNow == null) {
            sb.append("👥 Подписчики: Telegram не ответил\n");
        } else {
            if (saveSnapshot) {
                statsRepository.save(new ChannelDailyStats().setDay(today).setSubscribers(subscribersNow));
            }
            sb.append("👥 Подписчики: ").append(subscribersNow);
            String day = formatGrowth(subscribersNow, statsRepository.findById(today.minusDays(1)), "за сутки");
            String week = formatGrowth(subscribersNow, statsRepository.findById(today.minusDays(7)), "за неделю");
            if (!day.isEmpty() || !week.isEmpty()) {
                sb.append(" (").append(String.join(", ", List.of(day, week).stream().filter(s -> !s.isEmpty()).toList()))
                        .append(')');
            }
            sb.append('\n');
        }

        Instant now = Instant.now();
        UtmService.ChannelClicks dayClicks = utmService.countChannelClicksSince(now.minus(Duration.ofDays(1)));
        UtmService.ChannelClicks weekClicks = utmService.countChannelClicksSince(now.minus(Duration.ofDays(7)));
        sb.append("🔗 Из постов в бота: за сутки ").append(dayClicks.clicks())
                .append(" (впервые ").append(dayClicks.firstTime()).append("), за неделю ")
                .append(weekClicks.clicks()).append('\n');

        List<PostQueue> top = postQueueService.getTopReacted(Duration.ofDays(7), TOP_POSTS);
        if (!top.isEmpty()) {
            sb.append("❤️ Лучшие посты недели:\n");
            top.forEach(post -> sb.append("• #").append(post.getId()).append(' ').append(escape(post.getTitle()))
                    .append(" — ").append(post.getReactions()).append('\n'));
        }
        return sb.toString();
    }

    static String formatGrowth(int now, Optional<ChannelDailyStats> before, String label) {
        if (before.isEmpty()) return "";
        int delta = now - before.get().getSubscribers();
        return (delta > 0 ? "+" : "") + delta + " " + label;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
