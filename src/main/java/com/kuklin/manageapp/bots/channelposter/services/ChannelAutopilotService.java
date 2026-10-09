package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.entities.source.SourceItem;
import com.kuklin.manageapp.bots.channelposter.services.source.ContentSourceService;
import com.kuklin.manageapp.bots.channelposter.services.source.SourceItemService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ReviewPosterUpdateHandler.CHANNEL_ZONE;
import static com.kuklin.manageapp.bots.channelposter.telegram.handlers.ReviewPosterUpdateHandler.TIME_FORMAT;

/**
 * Чтобы канал жил без ручного присмотра:
 * автопилот ставит в очередь сильные посты, которые админ не проверил,
 * а утренний отчёт показывает, что с очередью и источниками.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelAutopilotService {

    private final ChannelPosterBotKeyComponent keys;
    private final ChannelPosterTelegramBot bot;
    private final PostQueueService postQueueService;
    private final SourceItemService itemService;
    private final ContentSourceService sourceService;
    private final PostReviewService reviewService;
    private final ChannelPosterProperties properties;

    /**
     * @return сколько постов автопилот поставил в очередь
     */
    public int runAutopilot() {
        if (!reviewService.isAutopilotEnabled()) return 0;

        int queued = 0;
        for (PostQueue post : postQueueService.getReviewSentBefore(Instant.now().minus(properties.getAutopilotAfter()))) {
            if (!reviewService.isAutopilotCandidate(post)) continue;
            PostQueue result = postQueueService.assignNextAvailableSlot(post, CHANNEL_ZONE);
            if (result == null) {
                log.info("Autopilot: no free slot for post #{}", post.getId());
                break;
            }
            queued++;
            notifyAdmins("🤖 Автопилот: пост #" + post.getId() + " «" + safe(post.getTitle()) + "» в очереди на "
                    + result.getScheduledAt().atZone(CHANNEL_ZONE).format(TIME_FORMAT)
                    + ". Передумаешь — /q покажет очередь.");
        }
        return queued;
    }

    /**
     * Снимает с конвейера посты, которые висят на проверке дольше channelposter.review-ttl:
     * статус EXPIRED, место в лимите pipeline-target освобождается и генерация продолжается.
     * Пост и картинка остаются — кнопки превью (и /q ID) работают, его можно поставить в очередь позже.
     *
     * @return сколько постов снято
     */
    public int expireStaleReviews() {
        List<PostQueue> stale = postQueueService.getReviewSentBefore(Instant.now().minus(properties.getReviewTtl()));
        if (stale.isEmpty()) return 0;

        StringBuilder titles = new StringBuilder();
        for (PostQueue post : stale) {
            postQueueService.markExpired(post);
            titles.append("\n• #").append(post.getId()).append(' ').append(safe(post.getTitle()));
        }
        log.info("Expired {} posts in review longer than {}", stale.size(), properties.getReviewTtl());
        notifyAdmins("⏳ Сняты с конвейера — не проверены за " + PostReviewService.humanize(properties.getReviewTtl())
                + " (" + stale.size() + "):" + titles
                + "\nКнопки под их превью ещё работают, /q ID пришлёт превью заново.");
        return stale.size();
    }

    public void sendDailyReport() {
        notifyAdmins(buildReport());
    }

    public String buildReport() {
        Instant now = Instant.now();
        Instant dayAgo = now.minus(Duration.ofDays(1));
        List<PostQueue> queued = postQueueService.getQueuedOrdered();
        long inReview = postQueueService.getPostsByStatus(PostQueue.PostQueueStatus.REVIEW).size();
        Optional<PostQueue> lastSent = postQueueService.getLastSent();

        StringBuilder sb = new StringBuilder("📊 <b>Канал: утренний отчёт</b>\n\n");

        if (lastSent.isPresent() && lastSent.get().getSentAt() != null) {
            Instant sentAt = lastSent.get().getSentAt();
            long days = Duration.between(sentAt, now).toDays();
            sb.append("Последний пост: ").append(sentAt.atZone(CHANNEL_ZONE).format(TIME_FORMAT));
            sb.append(days == 0 ? " (сегодня)" : " (" + days + " дн. назад)").append('\n');
        } else {
            sb.append("Последний пост: ещё не было\n");
        }

        sb.append("В очереди: ").append(queued.size());
        if (!queued.isEmpty()) {
            Instant until = queued.get(queued.size() - 1).getScheduledAt();
            sb.append(" (до ").append(until.atZone(CHANNEL_ZONE).format(TIME_FORMAT)).append(')');
        }
        sb.append('\n');
        sb.append("На проверке: ").append(inReview).append('\n');
        sb.append("Материалы за сутки: собрано ").append(itemService.countParsedSince(dayAgo))
                .append(", ждут фильтра ").append(itemService.countByStatus(SourceItem.Status.NEW))
                .append(", одобрено и ждут поста ").append(itemService.countByStatus(SourceItem.Status.APPROVED))
                .append('\n');

        List<ContentSource> broken = sourceService.getActive().stream()
                .filter(s -> s.getFailCount() != null && s.getFailCount() > 0)
                .toList();
        if (!broken.isEmpty()) {
            sb.append("\n⚠️ Источники с ошибками:\n");
            broken.forEach(s -> sb.append("• #").append(s.getId()).append(' ').append(safe(s.getName()))
                    .append(" — ").append(safe(s.getLastError()))
                    .append(" (").append(s.getFailCount()).append(" раз подряд)\n"));
        }

        if (queued.isEmpty()) {
            sb.append("\n⚠️ Очередь пуста. ");
            sb.append(inReview > 0
                    ? "Посты ждут проверки — пролистай превью выше."
                    : "Запусти сбор: /src run");
        }
        return sb.toString();
    }

    private void notifyAdmins(String text) {
        for (Long adminId : keys.getAdminIds()) {
            try {
                bot.sendReturnedMessage(adminId, text);
            } catch (Exception e) {
                log.error("Can't notify admin {}: {}", adminId, e.getMessage());
            }
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
