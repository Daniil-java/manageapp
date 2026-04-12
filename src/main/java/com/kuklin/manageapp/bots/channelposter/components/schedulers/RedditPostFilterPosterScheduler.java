package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.channelposter.entities.parser.RedditPost;
import com.kuklin.manageapp.bots.channelposter.services.parser.RedditPostService;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedditPostFilterPosterScheduler implements ScheduleProcessor {
    private final RedditPostService redditPostService;
    private final ChannelPosterBotKeyComponent botKeyComponent;
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROMPT =
            """
                       Ты — строгий и прагматичный шеф-редактор Telegram-канала «Радио Метаболизм». Канал посвящен адекватному биохакингу, доказательной нутрициологии, похудению, набору массы и лайфхакам по питанию. Канал тесно связан с AI-ботом для подсчета КБЖУ по фото и аудио, поэтому наша аудитория ценит технологии, логику и практическую пользу.
                       
                       Твоя задача — отфильтровать список спарсенных постов и оставить только те, которые идеально подойдут для адаптации или публикации в нашем канале.
                       
                       КРИТЕРИИ ОТБОРА (СТРОГО):
                       1. Тематика: Диеты, правильное питание (без фанатизма), похудение, набор мышечной массы, разбор продуктов, пищевые привычки.
                       2. Научность и адекватность: Никакого мракобесия (чистки от шлаков, детоксы, астрологические диеты — сразу в мусор). Только evidence-based подход.
                       3. Доступность (Правило "Микроба Валеры"): Контент не должен быть слишком сложным, узкоспециализированным или душным. Статьи про "влияние экспрессии генов на микробиом кишечника Валеры в третьем поколении" — отклонять. Нам нужна прикладная наука, которую можно применить в жизни.
                       4. Польза: Лайфхаки, подборки еды, развенчание мифов — это отлично.
                       
                       ТВОЙ АЛГОРИТМ РАБОТЫ:
                       Я передам тебе список постов в формате: Title, Content (текст (ограничен) или ссылка), Author, Score, CommentsCount.\s
                       Для каждого поста ты должен дать краткий вердикт.
                       
                       ФОРМАТ ОТВЕТА (СТРОГО):
                       Верни ТОЛЬКО валидная строка, содержащий ID постов, которые прошли отбор.\s
                       Запрещено писать любой другой текст, комментарии, приветствия или причины.\s
                       Запрещено использовать markdown-разметку (никаких ```json и ```).\s
                       ID должны быть написаны через пробел
                       
                       Пример идеального ответа:
                       10423 10425 10500
                       
                       Список постов: %s 
                    """;

    @Override
    public void process() {
        List<RedditPost> redditPosts = redditPostService.getByStatus(RedditPost.PostStatus.NEW);
        log.info("{} find {} NEW reddit posts!", getSchedulerName(), redditPosts.size());
        String raw = openAiProviderProcessor.fetchResponse(
                botKeyComponent.getAiKey(),
                String.format(PROMPT, buildAiInputOrNull(redditPosts)),
                BotIdentifier.CHANNEL_POSTER,
                getSchedulerName() + " process",
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );

        try {
            // Парсим строку напрямую в список ID
//            List<Long> approvedIds = objectMapper.readValue(raw, new TypeReference<List<Long>>() {});

            if (!(raw == null || raw.isBlank())) {
                List<Long> approvedIds = Arrays.stream(raw.split(" "))
                        .map(Long::parseLong)
                        .toList();

                // Обновляем статусы в базе
                if (approvedIds != null && !approvedIds.isEmpty()) {
                    log.info("{} has {} approved Ids!", getSchedulerName(), approvedIds.size());
                    redditPostService.updateStatuses(approvedIds, RedditPost.PostStatus.APPROVED);

                    // Все, что не попало в список approved, помечаем как REJECTED
                    List<Long> allIds = redditPosts.stream().map(RedditPost::getId).toList();
                    List<Long> rejectedIds = allIds.stream()
                            .filter(id -> !approvedIds.contains(id))
                            .toList();

                    redditPostService.updateStatuses(rejectedIds, RedditPost.PostStatus.REJECTED);
                }
            } else {
                // Если массив пустой, значит ИИ ничего не выбрал
                log.info("{} doesn't have approved Ids!", getSchedulerName());
                redditPostService.updateStatuses(
                        redditPosts.stream().map(RedditPost::getId).toList(),
                        RedditPost.PostStatus.REJECTED
                );
            }

        } catch (Exception e) {
            log.error("Failed to parse AI response for Reddit posts. Raw: {}", raw, e);
        }
    }

    public String buildAiInputOrNull(List<RedditPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        int i = 1;
        for (RedditPost post : posts) {
            sb.append("Пост ").append(i++).append(":\n")
                    .append("ID: ").append(post.getId()).append("\n")
                    .append("Title: ").append(safe(post.getTitle())).append("\n")
                    .append("Content: ").append(safeSubstring(safe(post.getContent()), 300)).append("\n")
                    .append("Author: ").append(safe(post.getAuthor())).append("\n")
                    .append("Score: ").append(post.getScore()).append("\n")
                    .append("Comments: ").append(post.getCommentsCount()).append("\n")
                    .append("\n---\n\n");
        }

        return sb.toString();
    }

    public static String safeSubstring(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
