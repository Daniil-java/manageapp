package com.kuklin.manageapp.bots.channelposter.components;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import com.kuklin.manageapp.bots.channelposter.entities.TopicCategory;
import com.kuklin.manageapp.bots.channelposter.model.AiGeneratedContent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Рубрики без источника: «Миф или правда», «Факт».
 * Два задания для AI: пополнить банк тем (generateTopics) и написать пост по теме из банка (generate).
 * Тему выбирает код (RubricTopicService), а не модель — иначе она раз за разом берёт самые известные мифы.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RubricContentGenerator {

    /** Разделы банка тем: посты рубрики чередуют их, чтобы не было трёх постов про сахар подряд. */
    public static final List<String> SECTIONS = List.of(
            "белок", "углеводы и сахар", "жиры", "вода и напитки", "продукты", "режим питания",
            "метаболизм и вес", "голод и аппетит", "сон", "тренировки", "добавки и витамины", "пищеварение");
    /** Раздел тем, которые админ добавил сам (/topics add) или AI отнёс не туда. */
    public static final String OTHER_SECTION = "другое";
    public static final int TOPICS_PER_FILL = 40;

    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final ChannelPosterBotKeyComponent component;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopicIdea(String section, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TopicsResponse(List<TopicIdea> topics) {
    }

    private record RubricPrompts(String topics, String post) {
    }

    private static final String RELIABILITY_RULES =
            """
                    Достоверность — главное:
                    - Опирайся только на то, что подтверждено систематическими обзорами, метаанализами или позициями ВОЗ, EFSA, крупных диетологических ассоциаций.
                    - НЕ придумывай названия исследований, авторов, журналы, университеты и точные цифры. Пиши «метаанализы показывают», «по данным ВОЗ» — только если уверен. Не уверен в цифре — обойдись без неё.
                    - Не давай медицинских назначений и не обещай результатов.
                    """;

    private static final String TOPICS_HEAD =
            """
                    Ты составляешь банк тем для Telegram-канала о питании, похудении и здоровых привычках.
                    """;

    private static final String TOPICS_TAIL =
            """

                    Требования:
                    - Каждая тема — одна короткая фраза на русском, до 120 символов.
                    - Разные разделы, примерно поровну. Раздел — строго одно значение из списка: %SECTIONS%.
                    - Не повторяй и не перефразируй темы, которые уже есть в банке (список ниже). Две темы об одном и том же — тоже повтор.
                    - Только то, где есть научный консенсус. Спорные темы, где учёные расходятся, не бери.

                    Уже в банке:
                    %EXISTING%

                    Верни JSON: {"topics": [{"section": "раздел", "text": "тема"}, ...]} — ровно %COUNT% тем. Только JSON, без пояснений.
                    """;

    private static final String POST_HEAD =
            """
                    Ты — автор Telegram-канала о питании, похудении и здоровых привычках: приветливый и эрудированный приятель, без тона наставника и морализаторства. Пост всегда на русском.
                    Подготовь два элемента: пост для рубрики канала и промпт для генерации изображения.

                    """ + PostPromptParts.JSON_FORMAT + """

                    ---
                    ИНСТРУКЦИЯ ДЛЯ "post_text":
                    """;

    private static final String POST_TAIL =
            """

                    """ + RELIABILITY_RULES + """

                    Объём: СТРОГО не длиннее 800 символов вместе с разметкой — пост уйдёт подписью к фото. Короткие абзацы по 1–2 предложения.

                    """ + PostPromptParts.MARKUP_RULES + """
                    - Ссылок не пиши.

                    """ + PostPromptParts.STYLE_RULES + """

                    ---
                    """ + PostPromptParts.IMAGE_RULES + """

                    Тема поста (раздел «%SECTION%»): %TOPIC%

                    """ + PostPromptParts.JSON_ONLY;

    private static final Map<TopicCategory.TopicType, RubricPrompts> PROMPTS = new EnumMap<>(Map.of(
            TopicCategory.TopicType.MYTH, new RubricPrompts(
                    """
                            Рубрика «Миф или правда». Придумай %COUNT% новых тем — распространённых убеждений о питании, похудении, обмене веществ, продуктах и тренировках, которые люди повторяют друг другу.
                            Тема — само убеждение, как его говорят люди: «Есть после шести — толстеть», «Яйца повышают холестерин».
                            Большинство — мифы, но примерно каждое пятое убеждение пусть окажется правдой или правдой наполовину: так рубрика не предсказуема.
                            """,
                    """
                            Рубрика «Миф или правда». Тема поста — убеждение, которое повторяют люди. Разбери его честно по научному консенсусу: вердикт решай сам, не подгоняй под «миф». Если наука здесь неоднозначна — вердикт «Наполовину правда» и честно скажи, что известно, а что нет.

                            Структура:
                            - Первая строка — само утверждение жирным, в кавычках: <b>«Есть после шести — толстеть»</b>.
                            - Вторая строка — вердикт: «❌ Миф», «✅ Правда» или «🤏 Наполовину правда».
                            - Почему так: механизм простыми словами, через бытовую аналогию.
                            - Откуда взялось убеждение — одной фразой, без насмешки над теми, кто в него верит.
                            - Что из этого следует на практике — как идея на заметку, не как указание.
                            В "post_title" — само утверждение без кавычек и вердикта.
                            """),
            TopicCategory.TopicType.FACT, new RubricPrompts(
                    """
                            Рубрика «Факт». Придумай %COUNT% новых тем — удивительных, но надёжно подтверждённых фактов о еде, продуктах, пищеварении, обмене веществ, аппетите, сне и тренировках.
                            Тема — сам факт одной фразой: «Слизистая желудка обновляется за несколько дней». Не банальности вроде «овощи полезны» — то, о чём хочется рассказать другу.
                            """,
                    """
                            Рубрика «Факт». Тема поста — один удивительный, но надёжно подтверждённый факт. Если в формулировке темы есть неточность — поправь её в посте, а не повторяй.

                            Структура:
                            - Первая строка — сам факт жирным, коротко и с крючком.
                            - Как это работает: механизм простыми словами, через бытовую аналогию.
                            - Что из этого следует для обычного человека — как идея на заметку, не как указание.
                            В "post_title" — суть факта одной фразой.
                            """)
    ));

    public boolean supports(TopicCategory.TopicType type) {
        return PROMPTS.containsKey(type);
    }

    /**
     * Пост по теме из банка. Высокая температура — посты одной рубрики не звучат по шаблону.
     *
     * @return пост или null, если AI не ответил или ответ не разобрался
     */
    public AiGeneratedContent generate(TopicCategory.TopicType type, RubricTopic topic) {
        String prompt = buildPostPrompt(type, topic);
        if (prompt == null) return null;

        String raw = openAiProviderProcessor.fetchCreativeJsonResponse(
                component.getAiKey(),
                prompt,
                BotIdentifier.CHANNEL_POSTER,
                "rubric " + type.name().toLowerCase() + " post",
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );
        if (raw == null) return null;
        try {
            AiGeneratedContent content = objectMapper.readValue(
                    ArticleContentGenerator.cleanJson(raw.trim()), AiGeneratedContent.class);
            if (content.getPostText() == null || content.getPostText().isBlank()) return null;
            return content;
        } catch (Exception e) {
            log.error("Rubric {}: can't parse post JSON: {}", type, e.getMessage());
            return null;
        }
    }

    /**
     * Новые темы для банка. Дубли и пустые строки отсеивает RubricTopicService.
     *
     * @param existing все темы рубрики в банке, включая выключенные, — AI их не повторяет
     * @return темы или пустой список, если AI не ответил
     */
    public List<TopicIdea> generateTopics(TopicCategory.TopicType type, List<String> existing) {
        String prompt = buildTopicsPrompt(type, existing);
        if (prompt == null) return List.of();

        String raw = openAiProviderProcessor.fetchJsonResponse(
                component.getAiKey(),
                prompt,
                BotIdentifier.CHANNEL_POSTER,
                "rubric " + type.name().toLowerCase() + " topics",
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );
        if (raw == null) return List.of();
        try {
            TopicsResponse response = objectMapper.readValue(
                    ArticleContentGenerator.cleanJson(raw.trim()), TopicsResponse.class);
            return response.topics() == null ? List.of() : response.topics();
        } catch (Exception e) {
            log.error("Rubric {}: can't parse topics JSON: {}", type, e.getMessage());
            return List.of();
        }
    }

    // replace, а не format: в темах бывают символы %
    static String buildPostPrompt(TopicCategory.TopicType type, RubricTopic topic) {
        RubricPrompts prompts = PROMPTS.get(type);
        if (prompts == null) return null;
        return POST_HEAD + prompts.post() + POST_TAIL
                .replace("%SECTION%", topic.getSection())
                .replace("%TOPIC%", topic.getText());
    }

    static String buildTopicsPrompt(TopicCategory.TopicType type, List<String> existing) {
        RubricPrompts prompts = PROMPTS.get(type);
        if (prompts == null) return null;
        String count = String.valueOf(TOPICS_PER_FILL);
        String existingList = existing.isEmpty() ? "пока пусто" : "- " + String.join("\n- ", existing);
        return TOPICS_HEAD
                + prompts.topics().replace("%COUNT%", count)
                + TOPICS_TAIL
                .replace("%SECTIONS%", String.join(", ", SECTIONS))
                .replace("%COUNT%", count)
                .replace("%EXISTING%", existingList);
    }
}
