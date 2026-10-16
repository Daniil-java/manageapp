package com.kuklin.manageapp.bots.channelposter.services.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.channelposter.components.source.TextUtils;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.source.ContentSource;
import com.kuklin.manageapp.bots.channelposter.entities.source.SourceItem;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI-редактор: оценивает новые материалы по шкале 0–10. От channelposter.approve-score — в работу, ниже — в отказ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SourceItemFilterService {

    private static final int BATCH = 40;
    private static final int SUMMARY_CHARS = 450;
    private static final int RECENT_TITLES = 40;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC);

    private static final String PROMPT = """
            Ты — строгий и прагматичный шеф-редактор Telegram-канала «Радио Метаболизм». Канал про адекватный биохакинг, доказательную нутрициологию, похудение, набор массы и лайфхаки по питанию. Канал связан с AI-ботом для подсчёта КБЖУ по фото и голосу, поэтому аудитория ценит логику, технологии и практическую пользу.

            Оцени каждый материал из списка по шкале 0–10: насколько из него получится хороший пост для канала.

            9–10: свежее исследование или новость с понятным практическим выводом про еду, похудение, набор массы, аппетит, сон/активность в связке с питанием. Интересно широкой аудитории.
            7–8: хорошая тема по профилю канала, вывод чуть менее практичный, но пост получится живой.
            4–6: по теме, но слишком узко, сложно или скучно (молекулярные механизмы, экспрессия генов, узкие клинические группы); исследования только на животных или клетках — не выше 5.
            0–3: не по теме; реклама, продажа курсов и БАДов, приглашения в сообщества, опросы и мета-посты; мракобесие (детоксы, «шлаки», астрология); вопрос пользователя без содержания; повтор темы из списка недавних постов.

            Источник подсказывает формат: PubMed — абстракт исследования (рандомизированные исследования и метаанализы — плюс), RSS — научная новость или статья, Reddit — пост сообщества (личный опыт с цифрами годится, просто вопрос — нет).

            Недавние посты канала (не повторяй эти темы):
            %s

            ФОРМАТ ОТВЕТА — строго JSON-объект без пояснений вокруг:
            {"items": [{"id": 123, "score": 8, "reason": "кратко по-русски, до 12 слов"}]}
            Оцени КАЖДЫЙ материал из списка.

            Материалы:
            %s
            """;

    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final ChannelPosterBotKeyComponent keys;
    private final SourceItemService itemService;
    private final ContentSourceService sourceService;
    private final PostQueueService postQueueService;
    private final ChannelPosterProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record Verdict(int score, String reason) {
    }

    /**
     * Оценивает одну пачку NEW-материалов.
     *
     * @return сколько материалов одобрено
     */
    public int filterBatch() {
        int expired = itemService.expireOld();
        if (expired > 0) {
            log.info("Source items expired: {}", expired);
        }

        List<SourceItem> items = itemService.getNewForFilter(BATCH);
        if (items.isEmpty()) {
            return 0;
        }

        Map<Long, ContentSource> sources = sourceService.getAll().stream()
                .collect(Collectors.toMap(ContentSource::getId, Function.identity()));
        String prompt = PROMPT.formatted(recentTitles(), buildInput(items, sources));

        String raw = openAiProviderProcessor.fetchJsonResponse(
                keys.getAiKey(),
                prompt,
                BotIdentifier.CHANNEL_POSTER,
                "source item filter",
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );

        Map<Long, Verdict> verdicts;
        try {
            verdicts = parseVerdicts(raw);
        } catch (Exception e) {
            // материалы остаются NEW — попробуем в следующий запуск
            log.error("Source item filter: can't parse AI response: {}", TextUtils.truncate(raw, 500), e);
            return 0;
        }

        int approved = 0;
        for (SourceItem item : items) {
            Verdict v = verdicts.get(item.getId());
            if (v == null) {
                item.setStatus(SourceItem.Status.REJECTED).setAiReason("нет оценки AI");
                continue;
            }
            boolean ok = v.score() >= properties.getApproveScore();
            item.setAiScore(v.score())
                    .setAiReason(TextUtils.truncate(v.reason(), 500))
                    .setStatus(ok ? SourceItem.Status.APPROVED : SourceItem.Status.REJECTED);
            if (ok) approved++;
        }
        itemService.saveAll(items);
        log.info("Source item filter: {} rated, {} approved", items.size(), approved);
        return approved;
    }

    private String recentTitles() {
        List<String> titles = postQueueService.getRecentTitles(RECENT_TITLES);
        return titles.isEmpty() ? "(пока нет)" : titles.stream().map(t -> "- " + t).collect(Collectors.joining("\n"));
    }

    static String buildInput(List<SourceItem> items, Map<Long, ContentSource> sources) {
        StringBuilder sb = new StringBuilder();
        for (SourceItem item : items) {
            ContentSource source = sources.get(item.getSourceId());
            sb.append("ID: ").append(item.getId()).append('\n');
            if (source != null) {
                sb.append("Источник: ").append(source.getType()).append(" · ").append(source.getName()).append('\n');
            }
            if (item.getPublishedAt() != null) {
                sb.append("Дата: ").append(DATE.format(item.getPublishedAt())).append('\n');
            }
            if (item.getScore() != null) {
                sb.append("Рейтинг Reddit: ").append(item.getScore()).append('\n');
            }
            sb.append("Заголовок: ").append(item.getTitle()).append('\n');
            String summary = item.getSummary() != null ? item.getSummary() : item.getContent();
            if (summary != null) {
                sb.append("Анонс: ").append(TextUtils.truncate(summary.replace('\n', ' '), SUMMARY_CHARS)).append('\n');
            }
            sb.append("---\n");
        }
        return sb.toString();
    }

    /**
     * {"items":[{"id":1,"score":8,"reason":"..."}]} → id → оценка. Терпим обёртку ```json и массив без "items".
     */
    Map<Long, Verdict> parseVerdicts(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty AI response");
        }
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
        }
        JsonNode root = objectMapper.readTree(json);
        JsonNode list = root.isArray() ? root : root.path("items");
        if (!list.isArray()) {
            throw new IllegalArgumentException("no items array");
        }
        Map<Long, Verdict> result = new HashMap<>();
        for (JsonNode n : list) {
            if (!n.hasNonNull("id")) continue;
            int score = Math.max(0, Math.min(10, n.path("score").asInt(0)));
            result.put(n.get("id").asLong(), new Verdict(score, n.path("reason").asText("")));
        }
        return result;
    }
}
