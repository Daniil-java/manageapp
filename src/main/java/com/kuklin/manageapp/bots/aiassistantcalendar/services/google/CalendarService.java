package com.kuklin.manageapp.bots.aiassistantcalendar.services.google;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.GoogleComponents;
import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.bots.aiassistantcalendar.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.entities.GoogleCacheableCalendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.ActionKnot;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.CalendarEventAiResponse;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.AiMessageLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.GoogleCacheableCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserGoogleCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.utils.CalendarServiceUtils;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.kuklin.manageapp.bots.aiassistantcalendar.services.google.utils.CalendarServiceUtils.getCalendarListEntryBySummaryOrNull;
import static com.kuklin.manageapp.bots.aiassistantcalendar.services.google.utils.CalendarServiceUtils.resolveTimeZoneFromUtcOffsetHours;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {
    private static final String DEFAULT_SUMMARY = "CALEN BOT";
    private static final String DEFAULT_DESC = "Календарь созданный телеграм-ботом";
    private static final String DEFAULT_TZ = "Europe/Moscow";
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final AiMessageLogService aiMessageLogService;
    private final ObjectMapper objectMapper;
    private final Calendar calendarService;
    private final TelegramAiAssistantCalendarBotKeyComponents components;
    private final UserGoogleCalendarService userGoogleCalendarService;
    private final TokenService tokenService;
    private final GoogleCacheableCalendarService cacheableCalendarService;
    private final JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    private final GoogleComponents googleComponents;

    private static final String AI_REMOVE_REQUEST = """
            Проанализируй список событий из Google Календаря и запрос пользователя, чтобы определить, какие события нужно удалить.

            Список событий:
            %s

            Запрос пользователя:
            %s
                        
            Параметры времени (чтобы ты мог ориентироваться в словах через "30 минут" и тд):
                Таймзона в календаре пользователя: %s
                Время на машине, которая отправила это сообщение: %s

            Инструкция:
            1. Сравни запрос с каждым событием по следующим полям:
               - summary (название)
               - description (описание)
               - дата и время (start и end)
            2. Учитывай смысловое и словесное совпадение. Пользователь может описывать событие другими словами, указывать только часть названия или дату.
                Пример:
                    -Удали все встречи на завтра
                        Тебе нужно найти все события которые являются встречами
                    Встретилось какое-то уникально слово, например "бот"  найди события с этим словом. 
                    
            3. Если в запросе пользователь говорит «все события», «удали все на сегодня», «удали встречи за завтра» — выбери все подходящие по этим параметрам.
            4. Если упомянуто несколько названий, дат или временных периодов — выбери все совпадающие события.
            5. Ответ должен быть в строгом формате JSON‑массива строк с **eventId**:
            [
              "eventId1",
              "eventId2"
            ]
            6. Если совпадений нет — верни пустой массив: []
             ВЕРНИ ТОЛЬКО JSON, БЕЗ ЛИШНЕГО ТЕКСТА, КАВЫЧЕК, ОБРАМЛЕНИЙ ИЛИ КОММЕНТАРИЕВ.!!!
                    Запрещено добавлять Markdown, кодовые блоки (```), подсветку json, комментарии, пояснения, преамбулы.
                     
            """;
    private static final String AI_EDIT_REQUEST = """
            Проанализируй список событий Google Календаря и запрос пользователя, чтобы определить, какое событие он хочет изменить.

            Список событий:
            %s

            Запрос пользователя:
            %s

            Инструкция по поиску:
            1. Сравни название, описание, дату, время и смысловую близость текста запроса с каждым событием.
            2. Учитывай, что пользователь может:
               - Использовать синонимы ("встреча" ↔️ "собрание"),
               - Не упоминать точное время или дату,
               - Упомянуть часть названия.
            3. Если событие наиболее логично соответствует запросу — **выбери только его**.
            4. Если несколько подходят одинаково — выбери **то, у которого дата ближайшая к сегодняшней**.
            5. Если ничего не подходит — верни пустую строку "".

            ⚠️ Формат ответа:
            — Верни **только значение eventId** без кавычек, без комментариев, без кода, без Markdown.  
            Если нет совпадений — верни пустую строку "".
                        
            ВЕРНИ ТОЛЬКО СТРОКУ, БЕЗ ЛИШНЕГО ТЕКСТА, КАВЫЧЕК, ОБРАМЛЕНИЙ ИЛИ КОММЕНТАРИЕВ.!!!
                    Запрещено добавлять Markdown, кодовые блоки (```), подсветку json, комментарии, пояснения, преамбулы.
            """;

    /**
     * - пытается найти уже созданный календарь по данным из БД;
     * - пытается найти уже созданный календарь по уникальному имени;
     * - если нашёл —  возвращает его id;
     * - если не нашёл — создаёт новый календарь с дефолтной тамйзоной.
     */
    public String getOrCreateCalendarId(AssistantGoogleOAuth auth) throws TokenRefreshException, IOException {

        CalendarContext context = getCalendarContext(auth.getTelegramId());
        //Если календарь существует, у нас в БД, то мы возвращаем его ID
        if (checkCalendarExist(context)) {
            return context.getCalendarId();
        }

        //Если календаря нет в нашей БД, то мы ищем его в гугл аккаунте пользователя
        var listReq = context.getCalendar().calendarList().list();
        List<CalendarListEntry> items = listReq.execute().getItems();

        if (items != null) {
            CalendarListEntry item = getCalendarListEntryBySummaryOrNull(items, DEFAULT_SUMMARY);
            if (item != null) {
                return item.getId();
            }
        }

        //Если в гугл аккаунте - нет календаря - мы создаем новый
        return createNewCalendarAndGetId(context, DEFAULT_SUMMARY, DEFAULT_TZ);
    }

    public String getCalendarSettingsString(Long telegramId) throws TokenRefreshException, IOException {
        CalendarContext context = getCalendarContext(telegramId);
        com.google.api.services.calendar.model.Calendar existing =
                context.getCalendar()
                        .calendars()
                        .get(context.getCalendarId())
                        .execute();
        StringBuilder sb = new StringBuilder();

        sb
                .append("Календарь:").append("\n")
                .append(existing.getSummary()).append("\n")
                .append("Таймзона: ").append(existing.getTimeZone());

        return sb.toString();
    }

    public com.google.api.services.calendar.model.Calendar getCalendarByTelegramIdOrNull(Long telegramId) throws TokenRefreshException, IOException {
        CalendarContext context = getCalendarContext(telegramId);
        if (context.getCalendarId() == null) return null;
        com.google.api.services.calendar.model.Calendar existing =
                context.getCalendar()
                        .calendars()
                        .get(context.getCalendarId())
                        .execute();
        return existing;
    }

    private boolean checkCalendarExist(CalendarContext context) {
        try {
            String calendarId = context.getCalendarId();
            if (calendarId == null || context.getCalendar() == null) {
                return false;
            }

            com.google.api.services.calendar.model.Calendar existing =
                    context.getCalendar()
                            .calendars()
                            .get(context.getCalendarId())
                            .execute();

            return existing != null;
        } catch (IOException e) {
            return false;
        }
    }

    public String setNewTimeZoneOrNull(Long telegramId, Integer offset) throws IOException, TokenRefreshException {
        log.info("Setting new time zone!");
        CalendarContext context = getCalendarContext(telegramId);
        if (context.getCalendarId() == null) {
            log.info("calndar id == null");
            // String id = getOrCreateCalendarId(auth);
            return null;
        }
        String tz = resolveTimeZoneFromUtcOffsetHours(offset);
        log.info("Timezone: {}", tz);
        com.google.api.services.calendar.model.Calendar existing =
                context.getCalendar()
                        .calendars()
                        .get(context.getCalendarId())
                        .execute();

        log.info("calendar info: summary:{}, tz:{}", existing.getSummary(), existing.getTimeZone());
        if (existing.getTimeZone() == null || !existing.getTimeZone().equals(tz)) {
            log.info("Calendar set new timezone!");
            existing.setTimeZone(tz);

            context.getCalendar()
                    .calendars()
                    .update(existing.getId(), existing)
                    .execute();
        }
        return existing.getId();
    }

    private String createNewCalendarAndGetId(CalendarContext context, String summary, String tz) throws IOException {
        com.google.api.services.calendar.model.Calendar calendar =
                new com.google.api.services.calendar.model.Calendar();
        calendar.setSummary(summary)
                .setDescription(DEFAULT_DESC)
                .setTimeZone(tz);

        calendar = context.getCalendar().calendars().insert(calendar).execute();
        return calendar.getId();
    }

    public Event addEventInCalendar(CalendarContext calendarContext,
                                    CalendarEventAiResponse request, Long telegramId)
            throws IOException, TokenRefreshException {
        Event event = CalendarServiceUtils.normalizeEventRequest(
                request, getTimeZoneInCalendarOrNull(calendarContext));

        var entry = getCalendarOrNull(telegramId);

        Event inserted = calendarContext.getCalendar().events()
                .insert(calendarContext.getCalendarId(), event)
                .execute();

        log.info("Запрос на создание эвента в GOOGLE: \nМероприятие {},\nОписание {},\nНачало {},\nКонец {},\nТаймзона {}",
                inserted.getId(),
                inserted.getSummary(),
                inserted.getDescription(),
                inserted.getStart(),
                inserted.getEnd()
        );

        return inserted;
    }

    public void removeEventInCalendar(String eventId, Long telegramId) throws IOException, TokenRefreshException {
        CalendarContext calendarContext = getCalendarContext(telegramId);

        calendarContext.getCalendar().events()
                .delete(calendarContext.getCalendarId(), eventId)
                .execute();
    }

    public Event getEventById(CalendarContext calendarContext, String targetId) throws IOException {
        Calendar calendar = calendarContext.getCalendar();
        String calendarId = calendarContext.getCalendarId();

        return calendar
                .events()
                .get(calendarId, targetId)
                .execute();
    }

    public Event editEventInCalendar(CalendarContext calendarContext,
                                     String targetId, ActionKnot actionKnot)
            throws IOException, TokenRefreshException {
        Calendar calendar = calendarContext.getCalendar();
        String calendarId = calendarContext.getCalendarId();

        Event target = calendar
                .events()
                .get(calendarId, targetId)
                .execute();

        log.info("TARGET: " + target.getSummary() + "\n" + target.getDescription() + "\n" + target.getStart() + " \n" + target.getEnd());

        String tz = getTimeZoneInCalendarOrNull(calendarContext);
        Event patch = CalendarServiceUtils.buildPatchFromRequest(actionKnot.getCalendarEventAiResponse(), tz);
        log.info("PATCH: " + patch.getSummary() + "\n" + patch.getDescription() + "\n" + patch.getStart() + " \n" + patch.getEnd());


        Event updated = calendar.events()
                .patch(calendarId, target.getId(), patch)
                .setSendUpdates("all") // при необходимости уведомляем участников
                .execute();

        log.info("Обновлён ивент: id={}, summary={}, start={}, end={}",
                updated.getId(), updated.getSummary(), updated.getStart(), updated.getEnd());

        return updated;
    }

    public List<GoogleCacheableCalendar> listUserCalendarsOrNull(Long telegramId) throws TokenRefreshException {
        String accessToken = tokenService.ensureAccessTokenOrNull(telegramId);
        Calendar service = getCalendarService(accessToken);

        try {
            List<CalendarListEntry> list = service.calendarList().list().execute().getItems();

            cacheableCalendarService.saveListOfCalendarsAndRemoveAllOfAnother(list, telegramId);
            return cacheableCalendarService.findAllByTelegramId(telegramId);
        } catch (IOException e) {
            log.error("Google service execute error!", e);
            return null;
        } catch (Exception e) {
            log.error("Google service execute error!", e);
            return null;
        }
    }

    public CalendarListEntry getCalendarOrNull(Long telegramId) throws TokenRefreshException {
        CalendarContext calendarContext = getCalendarContext(telegramId);

        try {
            CalendarListEntry entry = calendarContext.getCalendar()
                    .calendarList().get(calendarContext.getCalendarId()).execute();
            return entry;
        } catch (IOException e) {
            log.error("Google service execute error!", e);
            return null;
        }
    }

    /**
     * @return List<Event> список мероприятий за год
     * @return null - если у пользователя не установлен календарь
     * @throws TokenRefreshException - авторизация просрочена или ошибка на стороне гугла
     */
    public List<Event> findEventsToRemoveForNextYear(ActionKnot actionKnot, Long telegramId, String tz) throws IOException, TokenRefreshException {
        String accessToken = tokenService.ensureAccessTokenOrNull(telegramId);
        String calendarId = getCalendarIdOrNull(telegramId, accessToken);
        if (calendarId == null) {
            return null;
        }

        List<Event> yearEvents = getNextYearEvents(telegramId);

        CalendarEventAiResponse calendarResponse = actionKnot.getCalendarEventAiResponse();

        if (calendarResponse.getDescription().equals("Добавлено через Telegram-бота"))
            calendarResponse.setDescription("");

        String request = String.format(
                AI_REMOVE_REQUEST,
                CalendarServiceUtils.getRequestByEventsList(yearEvents),
                "Название: " + calendarResponse.getSummary()
                        + ". Описание: " + calendarResponse.getDescription()
                        + ". Дата: " + calendarResponse.getStart(),
                tz,
                OffsetDateTime.now()
        );
        String aiResponse = openAiProviderProcessor.fetchResponse(
                components.getAiKey(), request,
                BotIdentifier.ASSISTANT_BOT,
                BotIdentifier.ASSISTANT_BOT + " FINDING EVENT"
        );
        aiMessageLogService.saveLog(request, aiResponse);
        List<String> eventIds = objectMapper.readValue(aiResponse, new TypeReference<List<String>>() {
        });

        // Используем Set для быстрого поиска
        Set<String> idsСoincided = new HashSet<>(eventIds);

        return yearEvents.stream()
                .filter(event -> idsСoincided.contains(event.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Провеяем уведомляли ли мы пользователя, об определенной задаче
     *
     * @return String eventId возвращает идентификатор мероприятия
     * @return null - если у пользователя не установлен календарь
     * @throws TokenRefreshException - авторизация просрочена или ошибка на стороне гугла
     */
    public String findEventIdForEditInYear(Long telegramId, ActionKnot actionKnot) throws IOException, TokenRefreshException {
        String accessToken = tokenService.ensureAccessTokenOrNull(telegramId);
        String calendarId = getCalendarIdOrNull(telegramId, accessToken);
        if (calendarId == null) {
            return null;
        }
        List<Event> yearEvents = getNextYearEvents(telegramId);

        String request = String.format(
                AI_EDIT_REQUEST,
                CalendarServiceUtils.getRequestByEventsList(yearEvents),
                actionKnot.getCalendarEventAiResponse().getSummary()
                        + ". Описание: " + actionKnot.getCalendarEventAiResponse().getDescription()
                        + ". Дата: " + actionKnot.getCalendarEventAiResponse().getStart()
        );
        String aiResponse = openAiProviderProcessor.fetchResponse(
                components.getAiKey(), request,
                BotIdentifier.ASSISTANT_BOT, BotIdentifier.ASSISTANT_BOT + "YEAR FINDING EVENT"
        );
        aiMessageLogService.saveLog(request, aiResponse);
        return aiResponse;
    }

    /**
     * Провеяем уведомляли ли мы пользователя, об определенной задаче
     *
     * @return String calendarId - если у пользователя есть календарь
     * @return null - если у пользователя не установлен календарь
     */
    private String getCalendarIdOrNull(Long telegramId, String accessToken) {
        boolean isAuth = accessToken != null;
        if (isAuth) {
            AssistantGoogleOAuth auth = tokenService.findByTelegramIdOrNull(telegramId);
            log.info(auth.getDefaultCalendarId());
            if (auth.getDefaultCalendarId() != null) {
                return auth.getDefaultCalendarId();
            }
        }
        String calendarId = userGoogleCalendarService.getUserCalendarIdByTelegramIdOrNull(telegramId);
        if (calendarId != null) {
            return calendarId;
        }
        return null;
    }

    private Calendar getCalendarService(String accessToken) {
        if (accessToken != null) {
            log.info("CALENDAR INSTANCE: AUTH");
            return createCalendarServiceOrNull(accessToken);
        } else {
            log.info("CALENDAR INSTANCE: NO-AUTH");
            return calendarService;
        }
    }

    private Calendar createCalendarServiceOrNull(String accessToken) {
        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = buildCredential(accessToken, httpTransport);
            return new Calendar.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("ManageApp")
                    .build();
        } catch (Exception e) {
            log.error("Calendar service error!", e);
            return null;
        }
    }

    public CalendarContext getCalendarContext(Long telegramId) throws TokenRefreshException {
        String accessToken = tokenService.ensureAccessTokenOrNull(telegramId);
        return new CalendarContext()
                .setAccessToken(accessToken)
                .setCalendar(getCalendarService(accessToken))
                .setCalendarId(getCalendarIdOrNull(telegramId, accessToken))
                ;
    }

    private Credential buildCredential(String accessToken, NetHttpTransport httpTransport) {
        // Собираем минимальный Credential с client auth, чтобы можно было рефрешить токен
        String clientId = googleComponents.getClientId();
        String clientSecret = googleComponents.getClientSecret();

        Credential.Builder builder = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setTokenServerUrl(new GenericUrl("https://oauth2.googleapis.com/token")) //TODO Взять из переменной
                .setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret)
                );

        Credential credential = builder.build();

        credential.setAccessToken(accessToken);

        return credential;
    }

    public List<Event> getTodayEvents(Long telegramId) throws IOException, TokenRefreshException {
        CalendarContext context = getCalendarContext(telegramId);
        // Конвертируем в UTC для Google API
        ZoneId zoneId = ZoneId.of(getTimeZoneInCalendarOrNull(context));

        //Начало дня
        ZonedDateTime startOfDay = LocalDate.now(zoneId).atStartOfDay(zoneId);
        //Конец дня
        ZonedDateTime endOfDay = startOfDay.plusDays(1);

        // смещение в минутах
        int tzShiftMinutes = startOfDay.getOffset().getTotalSeconds() / 60;
        int tzShiftEnd = endOfDay.getOffset().getTotalSeconds() / 60;

        DateTime timeMin = new DateTime(startOfDay.toInstant().toEpochMilli(), tzShiftMinutes);
        DateTime timeMax = new DateTime(endOfDay.toInstant().toEpochMilli(), tzShiftEnd);

        // Запрос к Google Calendar API
        Events events = context.getCalendar().events().list(context.getCalendarId())
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute();

        return events.getItems();
    }

    public List<Event> getNextYearEvents(Long telegramId) throws IOException, TokenRefreshException {
        CalendarContext context = getCalendarContext(telegramId);
        ZoneId zoneId = ZoneId.of(getTimeZoneInCalendarOrNull(context));
        // Старт: начало сегодняшнего дня в TZ календаря
        ZonedDateTime start = LocalDate.now(zoneId).atStartOfDay(zoneId);
        // Конец окна: ровно через год
        ZonedDateTime end = start.plusYears(1);
        int tzShiftStart = start.getOffset().getTotalSeconds() / 60;
        int tzShiftEnd = end.getOffset().getTotalSeconds() / 60;
        DateTime timeMin = new DateTime(start.toInstant().toEpochMilli(), tzShiftStart);
        DateTime timeMax = new DateTime(end.toInstant().toEpochMilli(), tzShiftEnd);


        List<Event> all = new ArrayList<>();
        String pageToken = null;
        do {
            Events events = context.getCalendar().events().list(context.getCalendarId())
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setSingleEvents(true)        // разворачиваем повторяющиеся
                    .setOrderBy("startTime")
                    .setMaxResults(2500)          // максимум на страницу у Calendar API
                    .setPageToken(pageToken)
                    .execute();

            if (events.getItems() != null) {
                all.addAll(events.getItems());
            }
            pageToken = events.getNextPageToken();
        } while (pageToken != null);

        return all;
    }

    public String getTimeZoneInCalendarOrNull(CalendarContext context) throws IOException {
        if (context.getCalendarId() == null) return null;
        com.google.api.services.calendar.model.Calendar calendar =
                context.getCalendar().calendars().get(context.getCalendarId()).execute();

        return calendar.getTimeZone();
    }

    //Используется без авторизации пользователя.
    //Используется для верификации календаря
    public boolean existConnectionCalendarWithNoAuth(String calendarId) {
        try {
            com.google.api.services.calendar.model.Calendar calendar =
                    calendarService.calendars().get(calendarId).execute();
            return calendar != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Data
    @Accessors(chain = true)
    @RequiredArgsConstructor
    public class CalendarContext {
        private String accessToken;
        private Calendar calendar;
        private String calendarId;
    }
}
