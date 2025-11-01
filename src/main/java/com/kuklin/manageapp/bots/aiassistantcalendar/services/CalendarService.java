package com.kuklin.manageapp.bots.aiassistantcalendar.services;

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
import com.kuklin.manageapp.bots.aiassistantcalendar.models.ActionKnot;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.CalendarEventAiResponse;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.utils.CalendarServiceUtils;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.GoogleCacheableCalendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.GoogleCacheableCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final ObjectMapper objectMapper;
    private final Calendar calendarService;
    private final TelegramAiAssistantCalendarBotKeyComponents components;
    private final UserGoogleCalendarService userGoogleCalendarService;
    private final TokenService tokenService;
    private final GoogleCacheableCalendarService cacheableCalendarService;
    private final JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    private final GoogleComponents googleComponents;

    private static final String AI_REMOVE_REQUEST =
            """
                    Проанализируй список событий и строку поиска.
                                        
                    Список событий:
                    "%s"
                                        
                    Строка поиска:
                    "%s"
                                        
                    Правила:
                    1. Верни только JSON‑массив строк без лишнего текста, обрамлений или комментариев.
                    2. Нужно найти все события, у которых summary или description или дата (или их комбинация) максимально совпадают со строкой поиска.
                    3. Ответ должен быть в формате:
                    [
                      "eventId1",
                      "eventId2"
                    ]
                    4. Если совпадений нет — верни пустой массив [].
                                       
                    ВЕРНИ ТОЛЬКО JSON, БЕЗ ЛИШНЕГО ТЕКСТА, КАВЫЧЕК, ОБРАМЛЕНИЙ ИЛИ КОММЕНТАРИЕВ.!!!
                    Запрещено добавлять Markdown, кодовые блоки (```), подсветку json, комментарии, пояснения, преамбулы.
                     
                    """;

    private static final String AI_EDIT_REQUEST =
            """
                    Проанализируй список событий и строку поиска.
                                        
                    Список событий:
                    "%s"
                                        
                    Строка поиска:
                    "%s"
                                        
                    Правила:
                    1. Верни только строку с eventId без лишнего текста, обрамлений или комментариев.
                    2. Нужно найти только одно событие из всего списка, подходящее под строку поиска.
                    3. Если совпадений нет — верни пустую строку "".
                                       
                    ВЕРНИ ТОЛЬКО СТРОКУ, БЕЗ ЛИШНЕГО ТЕКСТА, КАВЫЧЕК, ОБРАМЛЕНИЙ ИЛИ КОММЕНТАРИЕВ.!!!
                    Запрещено добавлять Markdown, кодовые блоки (```), подсветку json, комментарии, пояснения, преамбулы.
                     
                    """;

    public Event addEventInCalendar(CalendarEventAiResponse request, Long telegramId) throws IOException, TokenRefreshException {
        CalendarContext calendarContext = getCalendarContext(telegramId);

        Event event = CalendarServiceUtils.normalizeEventRequest(
                request, getTimeZoneInCalendar(calendarContext.getCalendarId()));

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

    public Event editEventInCalendar(String targetId, ActionKnot actionKnot, Long telegramId) throws IOException, TokenRefreshException {
        CalendarContext calendarContext = getCalendarContext(telegramId);
        Calendar calendar = calendarContext.getCalendar();
        String calendarId = calendarContext.getCalendarId();

        Event target = calendar
                .events()
                .get(calendarId, targetId)
                .execute();

        String tz = getTimeZoneInCalendar(calendarId);
        Event patch = CalendarServiceUtils.buildPatchFromRequest(actionKnot.getCalendarEventAiResponse(), tz);

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
        log.info("access token returned");
        log.info("calendar service");
        Calendar service = getCalendarService(accessToken);

        try {
            log.info("before calendar list google request");
            List<CalendarListEntry> list = service.calendarList().list().execute().getItems();

            //TODO
//            List<CalendarListEntry> list = List.of(new CalendarListEntry().setId("id1").setSummary("summary"), new CalendarListEntry().setId("id2").setSummary("summary"), new CalendarListEntry().setId("id3").setSummary("summary"));
            log.info("after calendar list google request");
            log.info("cacheableCalendarService saveList");
            cacheableCalendarService.saveListOfCalendarsAndRemoveAllOfAnother(list, telegramId);
            log.info("cacheableCalendarService saved");
            return cacheableCalendarService.findAllByTelegramId(telegramId);
        } catch (IOException e) {
            log.error("Google service execute error!", e);
            return null;
        } catch (Exception e) {
            log.error("Google service execute error!", e);
            return null;
        }
    }

//    public CalendarListEntry getCalendarOrNull(Long telegramId, String accessToken) throws TokenRefreshException {
//        //TODO не вызывать календарь
//        CalendarContext calendarContext = getCalendarContext(telegramId);
//        Calendar service = getCalendarService(accessToken);
//
//        try {
//            return service.calendarList().get(calendarContext.getCalendarId()).execute();
//        } catch (IOException e) {
//            log.error("Google service execute error!", e);
//            return null;
//        }
//    }

    /**
     * Провеяем уведомляли ли мы пользователя, об определенной задаче
     * @return List<Event> список мероприятий за год
     * @return null - если у пользователя не установлен календарь
     * @throws TokenRefreshException - авторизация просрочена или ошибка на стороне гугла
     */
    public List<Event> findEventsToRemoveForNextYear(ActionKnot actionKnot, Long telegramId) throws IOException, TokenRefreshException {
        String accessToken = tokenService.ensureAccessTokenOrNull(telegramId);
        String calendarId = getCalendarIdOrNull(telegramId, accessToken);
        if (calendarId == null) {
            return null;
        }

        List<Event> yearEvents = getNextYearEvents(calendarId);

        String request = String.format(
                AI_REMOVE_REQUEST,
                CalendarServiceUtils.getRequestByEventsList(yearEvents),
                actionKnot.getCalendarEventAiResponse().getSummary()
                        + ". Описание: " + actionKnot.getCalendarEventAiResponse().getDescription()
                        + ". Дата: " + actionKnot.getCalendarEventAiResponse().getStart()
        );
        String aiResponse = openAiProviderProcessor.fetchResponse(
                components.getAiKey(), request);
        List<String> eventIds = objectMapper.readValue(aiResponse, new TypeReference<List<String>>() {});

        // Используем Set для быстрого поиска
        Set<String> idsСoincided = new HashSet<>(eventIds);

        return yearEvents.stream()
                .filter(event -> idsСoincided.contains(event.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Провеяем уведомляли ли мы пользователя, об определенной задаче
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
        List<Event> yearEvents = getNextYearEvents(calendarId);

        String request = String.format(
                AI_EDIT_REQUEST,
                CalendarServiceUtils.getRequestByEventsList(yearEvents),
                actionKnot.getCalendarEventAiResponse().getSummary()
                        + ". Описание: " + actionKnot.getCalendarEventAiResponse().getDescription()
                        + ". Дата: " + actionKnot.getCalendarEventAiResponse().getStart()
        );
        String aiResponse = openAiProviderProcessor.fetchResponse(
                components.getAiKey(), request);
        return aiResponse;
    }

    /**
     * Провеяем уведомляли ли мы пользователя, об определенной задаче
     * @return String calendarId - если у пользователя есть календарь
     * @return null - если у пользователя не установлен календарь
     */
    private String getCalendarIdOrNull(Long telegramId, String accessToken) {
        boolean isAuth = accessToken != null;
        if (isAuth) {
            log.info("authCalendar:");

            AssistantGoogleOAuth auth = tokenService.findByTelegramIdOrNull(telegramId);
            log.info(auth.getDefaultCalendarId());
            return auth.getDefaultCalendarId();
        }
        String calendarId = userGoogleCalendarService.getUserCalendarIdByTelegramIdOrNull(telegramId);
        if (calendarId != null) {
            log.info("userGoogleCalendarService: setter calendar");
            return calendarId;
        }
        return null;
    }

    private Calendar getCalendarService(String accessToken) {
        log.info("getCalendarService");

        if (accessToken != null) {
            log.info("CALENDAR INSTANCE: AUTH");
            return createCalendarServiceOrNull(accessToken);
        } else {
            log.info("CALENDAR INSTANCE: NO-AUTH");
            return calendarService;
        }
//        return accessToken != null ?
//                createCalendarServiceOrNull(accessToken):
//                calendarService;
    }

    private Calendar createCalendarServiceOrNull(String accessToken) {
        log.info("createCalendarServiceOrNull");
        try {
            log.info("httpTransport");
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            log.info("buildCredential");
            Credential credential = buildCredential(accessToken, httpTransport);
            log.info("builded Credential");
            return new Calendar.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("ManageApp")
                    .build();
        } catch (Exception e) {
            log.error("Calendar service error!", e);
            return null;
        }
    }

    private CalendarContext getCalendarContext(Long telegramId) throws TokenRefreshException {
        String accessToken = tokenService.ensureAccessTokenOrNull(telegramId);
        return new CalendarContext()
                .setAccessToken(accessToken)
                .setCalendar(getCalendarService(accessToken))
                .setCalendarId(getCalendarIdOrNull(telegramId, accessToken))
                ;
    }

    private Credential buildCredential(String accessToken, NetHttpTransport httpTransport) {
        // Собираем минимальный Credential с client auth, чтобы можно было рефрешить токен
        log.info("buildCredential");
        String clientId = googleComponents.getClientId();
        log.info("clientId: " + clientId.substring(0, 10) + "...");
        String clientSecret = googleComponents.getClientSecret();
        log.info("clientSecret: " + clientSecret.substring(0, 10) + "...");

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

    public List<Event> getTodayEvents(String calendarId) throws IOException {
        // Конвертируем в UTC для Google API
        ZoneId zoneId = ZoneId.of(getTimeZoneInCalendar(calendarId));

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
        Events events = calendarService.events().list(calendarId)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute();

        return events.getItems();
    }

    public List<Event> getNextYearEvents(String calendarId) throws IOException {
        ZoneId zoneId = ZoneId.of(getTimeZoneInCalendar(calendarId));
        // Старт: начало сегодняшнего дня в TZ календаря
        ZonedDateTime start = LocalDate.now(zoneId).atStartOfDay(zoneId);
        // Конец окна: ровно через год
        ZonedDateTime end = start.plusYears(1);
        int tzShiftStart = start.getOffset().getTotalSeconds() / 60;
        int tzShiftEnd   = end.getOffset().getTotalSeconds() / 60;
        DateTime timeMin = new DateTime(start.toInstant().toEpochMilli(), tzShiftStart);
        DateTime timeMax = new DateTime(end.toInstant().toEpochMilli(), tzShiftEnd);


        List<Event> all = new ArrayList<>();
        String pageToken = null;
        do {
            Events events = calendarService.events().list(calendarId)
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

    public String getTimeZoneInCalendar(String calendarId) throws IOException {
        com.google.api.services.calendar.model.Calendar calendar =
                calendarService.calendars().get(calendarId).execute();

        return calendar.getTimeZone();
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
