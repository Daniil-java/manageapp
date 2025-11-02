package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.ActionKnot;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.AiMessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionKnotService {
    private final OpenAiProviderProcessor aiService;
    private final ObjectMapper objectMapper;
    private final TelegramAiAssistantCalendarBotKeyComponents components;
    private final AiMessageLogService aiMessageLogService;

    private static final String AI_REQUEST =
            """
                    Проанализируй следующий текст и извлеки данные для события календаря.
                                        
                    Текст: "%s."
                                        
                    Правила:
                    1. Верни только JSON-объект без лишнего текста, обрамлений или комментариев.
                    2. Структура ответа:
                    {
                      "action": "", // EVENT_ADD или EVENT_DELETE или EVENT_EDIT
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
                    5. Продолжительность — если указано время начало, но не указано время конца, то дефолтно укажи время через 30 минут. Если не указано время начала, то время конца оставь null!
                    5. Продолжительность — если не указано время конца, оставь поле == null.
                    6. Если пользователь не назвал дату, считай, что сегодня %s.
                    7. Определи действие пользователя:
                       - Если он хочет добавить событие → action = "EVENT_ADD"
                       - Если он хочет удалить событие → action = "EVENT_DELETE"
                       - Если он хочет редактировать событие → action = "EVENT_EDIT". Заполни calendarEventAiResponse, НЕ НОВЫМ, А СТАРЫМ СОБЫТИЕМ, КОТОРОЕ ПОЛЬЗОВАТЕЛЬ ХОЧЕТ РЕДАКТИРОВАТЬ.
                    8. В конце описания добавь, даже если оно пустое, добавь слова "Добавлено через Telegram-бота"
                    9. Время нужно назвать в таймзоне %s, но это сообщение было отправлено в этом время: %s. Это на тот случай, если пользователь назовет не точное время, а скажет "через ... минут, часов и т.д". Это также относится к правилу 6, ты должен указать дату в таймзоне, указанной ранее.
                      ВЕРНИ ТОЛЬКО JSON, БЕЗ ЛИШНЕГО ТЕКСТА, ОБРАМЛЕНИЙ ИЛИ КОММЕНТАРИЕВ.!!!                  
                    """;

    private static final String AI_EDIT_REQUEST =
            """
                    Проанализируй следующий текст и извлеки данные для события календаря.
                                        
                    Текст: "%s."
                                        
                    Правила:
                    1. Верни только JSON-объект без лишнего текста, обрамлений или комментариев.
                    2. Структура ответа:
                    {
                      "action": "", // EVENT_EDIT
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
                    5. Продолжительность — если не указано время конца, оставь поле == null.
                    6. Если пользователь не назвал дату, считай, что сегодня %s.
                    7. Событие редактирования → action = "EVENT_EDIT". Заполни calendarEventAiResponse, НОВЫМ, А НЕ СТАРЫМ СОБЫТИЕМ.
                    8. В конце описания добавь, даже если оно пустое, добавь слова "Добавлено через Telegram-бота"
                      
                      ВЕРНИ ТОЛЬКО JSON, БЕЗ ЛИШНЕГО ТЕКСТА, ОБРАМЛЕНИЙ ИЛИ КОММЕНТАРИЕВ.!!!                  
                    """;

    public ActionKnot getActionKnotOrNull(String message, String tz) {
        String aiResponse = aiService.fetchResponse(
                components.getAiKey(),
                String.format(AI_REQUEST, message, LocalDate.now(), tz, OffsetDateTime.now()));
        aiResponse = extractResponse(aiResponse);
        aiMessageLogService.saveLog(message, aiResponse);
        try {
            return objectMapper.readValue(aiResponse, ActionKnot.class);
        } catch (JsonProcessingException e) {
            log.error("Не получилось распознать сообщение", e);
            return null;
        }
    }

    public ActionKnot getActionKnotForEditMessageOrNull(String message) {
        String aiResponse = aiService.fetchResponse(
                components.getAiKey(),
                String.format(AI_EDIT_REQUEST, message, LocalDate.now()));
        aiResponse = extractResponse(aiResponse);
        aiMessageLogService.saveLog(message, aiResponse);
        try {
            return objectMapper.readValue(aiResponse, ActionKnot.class);
        } catch (JsonProcessingException e) {
            log.error("Не получилось распознать сообщение", e);
            return null;
        }
    }

    private String extractResponse(String response) {
        if (response != null) {
            if (response.startsWith("```json")) {
                response = response.substring("```json".length()).trim();
            }

            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3).trim();
            }
        }
        return response;
    }
}
