package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.calendar.model.Event;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.ActionKnot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionKnotService {
    private final ObjectMapper objectMapper;
    private final TelegramAiAssistantCalendarBotKeyComponents components;
    private final AiMessageLogService aiMessageLogService;
    private final OpenAiProviderProcessor openAiProviderProcessor;

    private static final String AI_EDIT_REQUEST =
            """
                    Проанализируй следующий текст и извлеки данные для события календаря. Это событие редактирования, когда пользователь меняет свое старое мероприятие на новое. Я отправлю тебе также старое событие, чтобы ты мог в нем ориентироваться. Например если пользователь попросить перенсти на час вперед и тд.
                                        
                    Текст: "%s."
                    
                    Старое событие: "%s"
                    
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
                        "notifyInMinutesList": [] // Массив целых чисел. Каждый элемент — это количество минут до начала события,
                                                      // за которое нужно отправить напоминание.
                                                      // Если пользователь говорит: "напомни за X минут/часов/дней" или указывает несколько
                                                      // напоминаний ("за день и за час"), добавь все эти значения в МИНУТАХ.
                                                      // Если явного запроса на напоминания не было — верни пустой массив [].
                          }
                    }
                    3. Если в тексте нет описания — оставить пустую строку description.
                    4. Дату и время распознать из текста.
                    5. Продолжительность — такая же как была в старой задаче. Но если не указано время конца, оставь поле == null.
                    6. Если пользователь не назвал дату, считай, что сегодня %s.
                    7. Событие редактирования → action = "EVENT_EDIT". Заполни calendarEventAiResponse, НОВЫМ, А НЕ СТАРЫМ СОБЫТИЕМ.
                    8. В конце описания добавь, даже если оно пустое, добавь слова "Добавлено через Telegram-бота"
                      
                      ВЕРНИ ТОЛЬКО JSON, БЕЗ ЛИШНЕГО ТЕКСТА, ОБРАМЛЕНИЙ ИЛИ КОММЕНТАРИЕВ.!!!                  
                    """;

    private static final String AI_REQUEST = """
            Проанализируй пользовательскую команду и извлеки все данные для действий с событиями календаря.

            Текст:
            "%s"

            Твоя задача:
            Определи, что хочет сделать пользователь — добавить, удалить или изменить (редактировать) событие, и извлеки необходимые данные.

            ---

            ПРАВИЛА:

            1. Верни только корректный JSON без лишнего текста, форматирования, Markdown или комментариев.

            2. Структура ответа:
            {
              "action": "", // EVENT_ADD или EVENT_DELETE или EVENT_EDIT или ERROR
              "calendarEventAiResponse": {
                "summary": "",
                "description": "",
                "start": "",
                "end": "",
                "timezone": "",
                "result": "",          // краткий текст, какие данные распознаны и какие действия будут выполнены
                "isSuccessful": ""     // true или false
                "notifyInMinutesList": [] // Массив целых чисел. Каждый элемент — это количество минут ДО НАЧАЛА события,
                                          // за которое нужно отправить напоминание.
                                          //
                                          // ВАЖНО: различай два типа просьб пользователя по СМЫСЛУ:
                                          //
                                          // 1) Напоминание "ПЕРЕД началом события".
                                          //    Пользователь явно говорит, что напоминание должно прийти ЗАРАНЕЕ,
                                          //    перед началом задачи/встречи:
                                          //    - смысл фраз: "заранее", "до начала", "до встречи", "за X до",
                                          //      "чтобы заранее вспомнить", "за X минут до события" и т.п.
                                          //    Примеры формулировок:
                                          //      - "напомни за час до встречи"
                                          //      - "за 10 минут до начала"
                                          //      - "заранее за полчаса напомни"
                                          //
                                             //    В этом случае:
                                             //      - определи X (минуты/часы/дни по смыслу фразы),
                                             //      - переведи X в минуты,
                                             //      - добавь X в notifyInMinutesList как количество минут ДО начала события.
                                             //    Пример:
                                             //      "напомни за час до" → notifyInMinutesList = [60].
                                             //
                                             // 2) Напоминание "ЧЕРЕЗ X от текущего момента".
                                             //    Пользователь говорит, что напоминание должно прийти через некоторый
                                             //    промежуток времени ОТ СЕЙЧАС, а не "за X до начала".
                                             //    Это любые фразы по смыслу вида:
                                             //      - "через час напомни про задачу"
                                             //      - "через два часа напомни про встречу в 16:00"
                                             //      - "напомни мне через полчаса"
                                             //    Даже если нет слова "через", но по смыслу понятно, что речь о промежутке
                                             //    от текущего момента (например: "через N", "спустя N", "через N времени").
                                             //
                                             //    Алгоритм:
                                             //      - возьми текущее время отправки сообщения,
                                             //      - по смыслу фразы определи промежуток X (в минутах/часах/днях),
                                             //      - вычисли время напоминания remindAt = текущее_время + X,
                                             //      - определи время начала события start,
                                             //      - вычисли minutesBefore = (start - remindAt) в минутах,
                                             //        округлив до ближайшего целого,
                                             //      - если minutesBefore < 0 (remindAt позже начала события), используй 0,
                                             //      - добавь minutesBefore в notifyInMinutesList.
                                             //
                                             //    Пример:
                                             //      Сейчас 10:00, событие в 16:00.
                                             //      Фраза: "Задача в 4 часа, напомни мне через час".
                                             //      → remindAt = 11:00 (через час от текущего времени),
                                             //      → до события остаётся 5 часов = 300 минут,
                                             //      → notifyInMinutesList = [300].
                                             //
                                             // Если явного смысла "напомнить" нет — верни пустой массив [].
                  }
            }

            ---

            3. Как определить **действие (`action`)**:
               
               **EVENT_DELETE** → если пользователь говорит об удалении:
               - Использует слова: "удали", "удалить", "сотри", "отмени", "отменить", "стереть", "убери", "очисти".
               - Примеры:  
                 - "Удали встречу завтра"  
                    - примерное "summary": "Удалить встречу завтра - дата (если смог дату вычислить) "
                 - "Отмени запись в барбершоп"  
                 - "Убери задачу по проекту"
               - Если это удаление, тебе в summary нужно отразить: что удалить. когда удалить. В description - дополнительные параметры полезные для идентификации удаляемой задачи.

               **EVENT_EDIT** → если пользователь говорит об изменении существующего события:
               - Использует слова: "измени", "переименуй", "перенеси", "поставь позже", "добавь описание", "сделай позже", "перенести на".
               - Примеры:  
                 - "Перенеси встречу на завтра в 10"  
                 - "Измени время собрания"  
                 - "Добавь описание к задаче"

               **EVENT_ADD** → во всех других случаях, когда пользователь говорит о новом событии:
               - Использует глаголы или формулировки вроде: "создай", "добавь", "запиши", "напомни", "встреча с", "собрание", "пойти", "надо".
               - Примеры:  
                 - "Добавь встречу завтра в 14:00"  
                 - "Запланируй звонок с клиентом"  
                 - "Создай новое напоминание позвонить маме"

                **ERROR** → во всех случаях, когда присланное сообщение кажется случайным или случайным набором символов или это не похоже на задачу:
               - Примеры:  
                 - "афыдбдэа"  
                 - "tedf" 
                 - "Эээ... Привет"  
                 - "Тест тест"   
                 
               ⚠️ Если действие не удалось однозначно определить — выбери то, что логически наиболее вероятно:
               - Если речь идёт о **новом событии** → EVENT_ADD  
               - Если упоминаются **изменения старого** → EVENT_EDIT  
               - Если звучит **удаление / отмена** → EVENT_DELETE

            ---

            4. Обработка дат и времени:
            - Если указано относительное время ("через 2 часа", "через 15 минут") — вычисли его относительно времени отправки.
             Если пользователь не назвал дату, считай, что сегодня %s, но вычисли дату по временой зоне календаря.
             Время нужно назвать в таймзоне %s, но это сообщение было отправлено в этом время: %s. Это на тот случай, если пользователь назовет не точное время, а скажет "через ... минут, часов и т.д". Это также относится к правилу 6, ты должен указать дату в таймзоне, указанной ранее.

            5. Длительность:
            - Если указано только время начала → продолжительность 30 минут.
            - Если нет времени конца → "end" = null.

            6. В description напиши детали озвученные пользователем, которые не попали в summary. Если description отсутствует — оставь пустую строку, но ВСЕГДА добавляй в конец `"Добавлено через Telegram-бота"`.
                Например:

            7. Поле "result" — кратко объясни, что именно распознано.  
            
            8. Можешь добавить подходящий под название задачи смайлик, в название задачи перед текстом. 
            Поле "isSuccessful" = true, если удалось извлечь хотя бы summary и start-время.

            ---

            ⚠️ ВЕРНИ ТОЛЬКО JSON без пояснений, без Markdown, без кодовых блоков и без комментариев.
            """;


    public ActionKnot getActionKnotOrNull(String message, String tz) {
        // например: "Europe/Moscow"
        ZoneId userZone = ZoneId.of(tz);
        ZonedDateTime nowForGpt = ZonedDateTime.now(userZone);

        // "сегодня" в таймзоне пользователя
        String todayInUserZone = nowForGpt.toLocalDate().toString();
        // время обращения к GPT в той же таймзоне
        String nowForGptStr = nowForGpt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String aiResponse = openAiProviderProcessor.fetchResponse(
                components.getAiKey(),
                String.format(AI_REQUEST, message, todayInUserZone, tz, nowForGptStr),
                BotIdentifier.ASSISTANT_BOT,
                BotIdentifier.ASSISTANT_BOT + " Action knot request"
        );
        aiResponse = extractResponse(aiResponse);
        aiMessageLogService.saveLog(message, aiResponse);
        try {
            return objectMapper.readValue(aiResponse, ActionKnot.class);
        } catch (JsonProcessingException e) {
            log.error("Не получилось распознать сообщение", e);
            return null;
        }
    }

    public ActionKnot getActionKnotForEditMessageOrNull(String message, Event oldEvent) {
        String aiResponse = openAiProviderProcessor.fetchResponse(
                components.getAiKey(),
                String.format(AI_EDIT_REQUEST, message, getOldEventString(oldEvent), LocalDate.now()),
                BotIdentifier.ASSISTANT_BOT,
                BotIdentifier.ASSISTANT_BOT + " Edit Action knot request"
        );
        aiResponse = extractResponse(aiResponse);
        aiMessageLogService.saveLog(message, aiResponse);
        try {
            return objectMapper.readValue(aiResponse, ActionKnot.class);
        } catch (JsonProcessingException e) {
            log.error("Не получилось распознать сообщение", e);
            return null;
        }
    }

    private String getOldEventString(Event event) {
        StringBuilder sb = new StringBuilder();
        sb
                .append("Название: ").append(event.getSummary()).append("\n")
                .append("Описание: ").append(event.getDescription()).append("\n")
                .append("Начало: ").append(event.getStart()).append("\n")
                .append("Конец: ").append(event.getEnd());
        return sb.toString();
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
