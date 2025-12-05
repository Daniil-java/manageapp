package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.ActionKnot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionKnotService {
    private final OpenAiProviderProcessor aiService;
    private final ObjectMapper objectMapper;
    private final TelegramAiAssistantCalendarBotKeyComponents components;

    private static final String AI_REQUEST =
            """
                    Проанализируй следующий текст и извлеки данные для события календаря.
                                        
                    Текст: "%s."
                                        
                    Правила:
                    1. Верни только JSON-объект без лишнего текста, обрамлений или комментариев.
                    2. Структура ответа:
                    {
                      "action": "", // EVENT_ADD или EVENT_DELETE
                      "calendarEventAiResponse": {
                        "summary": "",        // краткое название события
                        "description": "",    // описание, если есть
                        "start": "",          // дата и время начала в формате yyyy-MM-dd'T'HH:mm:ss
                        "end": "",            // дата и время конца в таком же формате
                        "timezone": "",      \s
                        "result": "",         // Опиши действия которые ты сделал. Если не получилось извлечь данные - напиши об этом.
                        "isSuccessful": ""    // Boolean-ответ, true - если получилось извлечь данные, false - если не получилось извлечь название события и дату начала
                      }
                    }
                    3. Если в тексте нет описания — оставить пустую строку description.
                    4. Дату и время распознать из текста.
                    5. Продолжительность — 1 час, если не указано иное.
                    6. Таймзону вставить в поле "timezone" в формате UTC±3.
                    7. Если пользователь не назвал дату, считай, что сегодня %s.
                    8. Определи действие пользователя:
                       - Если он хочет добавить событие → action = "EVENT_ADD"
                       - Если он хочет удалить событие → action = "EVENT_DELETE"
                    ВЕРНИ ТОЛЬКО JSON!!!
                                        
                    """;

    public ActionKnot getActionKnotOrNull(String message) {
        String aiResponse = aiService.fetchResponse(
                components.getAiKey(),
                String.format(AI_REQUEST, message, LocalDate.now()));
        log.info(aiResponse);
        try {
            return objectMapper.readValue(aiResponse, ActionKnot.class);
        } catch (JsonProcessingException e) {
            log.error("Не получилось распознать сообщение", e);
            return null;
        }
    }
}
