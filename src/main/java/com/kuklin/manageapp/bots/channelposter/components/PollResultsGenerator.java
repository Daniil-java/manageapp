package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.channelposter.entities.ChannelPoll;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Комментарий к итогам квиза или опроса. Цифры в пост ставит код (PollResultsService), AI их не пересказывает
 * и не придумывает — только объясняет.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PollResultsGenerator {

    // длиннее — AI ушёл в лекцию, публикуем итоги без комментария
    static final int MAX_COMMENT_CHARS = 1200;

    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final ChannelPosterBotKeyComponent component;

    private static final String QUIZ_TASK =
            """
                    Ты — автор Telegram-канала о питании и здоровых привычках: приветливый эрудированный приятель. Вчера в канале был квиз, сегодня — пост с итогами. Цифры и правильный ответ бот уже поставил в начало поста.
                    Напиши продолжение поста — 2–3 коротких абзаца, всего до 500 символов:
                    - почему верен именно этот ответ: механизм простыми словами, через бытовую аналогию;
                    - если много людей выбрали неверный вариант — почему он кажется правильным, без насмешки.
                    """;

    private static final String POLL_TASK =
            """
                    Ты — автор Telegram-канала о питании и здоровых привычках: приветливый эрудированный приятель. Вчера в канале был опрос мнений, сегодня — пост с итогами. Цифры бот уже поставил в начало поста.
                    Напиши продолжение поста — 2–3 коротких абзаца, всего до 500 символов:
                    - что о теме вопроса говорит наука — применительно к самым популярным ответам;
                    - без оценки читателей и морализаторства: любой вариант ответа нормален, дай идею на заметку.
                    """;

    private static final String RULES =
            """

                    Не пересказывай проценты — они уже в посте. Не придумывай исследования, авторов и цифры; опирайся только на научный консенсус.
                    Начни сразу с сути, без «Итак» и без заголовка.

                    """ + PostPromptParts.MARKUP_RULES + """
                    - Ссылок не пиши.

                    """ + PostPromptParts.STYLE_RULES + """

                    Данные:
                    %DATA%

                    Верни только текст продолжения поста.
                    """;

    /**
     * @param data вопрос, варианты с процентами и (у квиза) правильный ответ с пояснением — простым текстом
     * @return комментарий или null — тогда итоги выходят без него
     */
    public String generateComment(ChannelPoll poll, String data) {
        try {
            String raw = openAiProviderProcessor.fetchCreativeResponse(
                    component.getAiKey(),
                    buildPrompt(poll, data),
                    BotIdentifier.CHANNEL_POSTER,
                    "poll results comment",
                    MetricsAiInteractionRecord.AiMessageType.TEXT
            );
            if (raw == null || raw.isBlank() || raw.length() > MAX_COMMENT_CHARS) return null;
            return raw.trim();
        } catch (Exception e) {
            log.error("Poll #{}: results comment failed: {}", poll.getId(), e.getMessage());
            return null;
        }
    }

    static String buildPrompt(ChannelPoll poll, String data) {
        return (poll.isQuiz() ? QUIZ_TASK : POLL_TASK) + RULES.replace("%DATA%", data);
    }
}
