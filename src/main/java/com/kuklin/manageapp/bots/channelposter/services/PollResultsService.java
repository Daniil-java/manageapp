package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.PollResultsGenerator;
import com.kuklin.manageapp.bots.channelposter.components.PostPublisher;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Итоги квизов и опросов: на следующий день после опроса — пост ответом на него.
 * Проценты и шкалы считает код, AI только комментирует. Голосов мало — итоги не публикуем, админам пишем.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PollResultsService {

    private static final int BAR_LENGTH = 10;
    // столько после срока итогов пробуем снова (раз в час), дальше — бросаем и пишем админам
    static final Duration GIVE_UP_AFTER = Duration.ofHours(24);

    private final ChannelPollService pollService;
    private final PostQueueService postQueueService;
    private final TopicCategoryService topicCategoryService;
    private final PollResultsGenerator generator;
    private final PostPublisher publisher;
    private final ChannelPosterProperties properties;
    private final ChannelPosterTelegramBot bot;
    private final ChannelPosterBotKeyComponent keys;
    private final ChannelFeedbackService feedbackService;

    /**
     * @return сколько постов с итогами вышло
     */
    public int publishDueResults() {
        int published = 0;
        for (ChannelPoll poll : pollService.getDueForResults()) {
            try {
                if (publishResults(poll)) published++;
            } catch (Exception e) {
                log.error("Poll #{}: results failed, will retry", poll.getId(), e);
            }
        }
        return published;
    }

    private boolean publishResults(ChannelPoll poll) {
        // сутки не получалось (Telegram, AI) — сдаёмся: иначе каждый час новые попытки и новый запрос к AI
        if (poll.getResultsDueAt() != null && poll.getResultsDueAt().isBefore(Instant.now().minus(GIVE_UP_AFTER))) {
            pollService.markResultsSent(poll, null);
            notifyAdmins("📊 Итоги «" + escape(poll.getQuestion()) + "» так и не вышли за сутки — бросаю попытки. "
                    + "Подробности в логах.");
            return false;
        }
        if (poll.getTotalVoters() < properties.getPollResultsMinVoters()) {
            pollService.markResultsSent(poll, null);
            notifyAdmins("📊 Итоги «" + escape(poll.getQuestion()) + "» не публикую: ответили " + poll.getTotalVoters()
                    + " (нужно от " + properties.getPollResultsMinVoters() + ").");
            return false;
        }

        String comment = generator.generateComment(poll, buildData(poll));
        String text = buildStats(poll) + "\n\n" + (comment != null ? comment : buildFallbackComment(poll));
        if (poll.getType() == ChannelPoll.Type.CALORIES) {
            // угадайка показывает калорийного бота в деле — ссылка на него всегда (не создалась — без неё)
            try {
                text += "\n\n" + ChannelFeedbackService.buildBotLinkLine(feedbackService.getBotLink(poll.getPostQueueId()));
            } catch (Exception e) {
                log.warn("Poll #{}: bot link not created: {}", poll.getId(), e.getMessage());
            }
        }
        PostQueue results = new PostQueue()
                .setCategoryId(topicCategoryService.getTopicCategoryByTopicTypeOrNull(TopicCategory.TopicType.QUIZ_ANSWER)
                        .map(TopicCategory::getId)
                        .orElse(null))
                .setTitle("Итоги: " + poll.getQuestion())
                .setTextContent(text.trim());

        Integer messageId = publisher.publishReply(keys.getChannelId(), results, poll.getTgMessageId());
        if (messageId == null && poll.getTgMessageId() != null) {
            // опрос могли удалить из канала — тогда ответить не на что: итоги обычным постом
            log.warn("Poll #{}: reply to the poll failed, sending results without reply", poll.getId());
            messageId = publisher.publishReply(keys.getChannelId(), results, null);
        }
        if (messageId == null) {
            // итоги не помечены — следующий запуск попробует снова
            log.warn("Poll #{}: Telegram didn't accept results", poll.getId());
            return false;
        }
        PostQueue saved = postQueueService.save(results
                .setStatus(PostQueue.PostQueueStatus.SENT)
                .setSentAt(Instant.now())
                .setTgMessageId(messageId));
        pollService.markResultsSent(poll, saved.getId());
        return true;
    }

    /**
     * Шапка поста: вопрос, варианты с процентами и шкалой, у квиза — отметка правильного ответа.
     */
    static String buildStats(ChannelPoll poll) {
        List<Integer> percents = getPercents(poll);
        StringBuilder sb = new StringBuilder("<b>📊 Итоги ").append(getTitle(poll)).append("</b>\n")
                .append("«").append(escape(poll.getQuestion())).append("»\n");
        for (int i = 0; i < poll.getOptions().size(); i++) {
            boolean correct = poll.isQuiz() && poll.getCorrectOptionId() == i;
            sb.append('\n').append(correct ? "✅ " : poll.isQuiz() ? "▫️ " : "• ")
                    .append(correct ? "<b>" : "").append(escape(poll.getOptions().get(i))).append(correct ? "</b>" : "")
                    .append(" — ").append(percents.get(i)).append("%\n")
                    .append(getBar(percents.get(i)));
        }
        sb.append("\n\nОтветили: ").append(poll.getTotalVoters());
        if (poll.isQuiz()) {
            sb.append(" · правильно — ").append(percents.get(poll.getCorrectOptionId())).append('%');
        }
        return sb.toString();
    }

    private static String getTitle(ChannelPoll poll) {
        return switch (poll.getType()) {
            case QUIZ -> "квиза";
            case REGULAR -> "опроса";
            case CALORIES -> "«Угадай калорийность»";
        };
    }

    // данные для AI простым текстом
    static String buildData(ChannelPoll poll) {
        List<Integer> percents = getPercents(poll);
        StringBuilder sb = new StringBuilder("Вопрос: ").append(poll.getQuestion()).append('\n');
        for (int i = 0; i < poll.getOptions().size(); i++) {
            sb.append("- ").append(poll.getOptions().get(i)).append(": ").append(percents.get(i)).append('%');
            if (poll.isQuiz() && poll.getCorrectOptionId() == i) sb.append(" (правильный)");
            sb.append('\n');
        }
        sb.append("Ответили: ").append(poll.getTotalVoters());
        if (poll.isQuiz()) {
            sb.append("\nПояснение к правильному ответу: ").append(poll.getExplanation());
        }
        return sb.toString();
    }

    // AI не ответил: у квиза есть пояснение, у опроса — просто спасибо
    private static String buildFallbackComment(ChannelPoll poll) {
        return poll.isQuiz() ? "💬 " + escape(poll.getExplanation()) : "Спасибо всем, кто ответил!";
    }

    static List<Integer> getPercents(ChannelPoll poll) {
        List<Integer> votes = poll.getOptionVotes();
        int total = poll.getTotalVoters();
        return IntStream.range(0, poll.getOptions().size())
                .mapToObj(i -> {
                    int v = votes == null || i >= votes.size() || votes.get(i) == null ? 0 : votes.get(i);
                    return total == 0 ? 0 : (int) Math.round(v * 100.0 / total);
                })
                .toList();
    }

    static String getBar(int percent) {
        int filled = (int) Math.round(percent / (100.0 / BAR_LENGTH));
        return "▓".repeat(filled) + "░".repeat(BAR_LENGTH - filled);
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

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
