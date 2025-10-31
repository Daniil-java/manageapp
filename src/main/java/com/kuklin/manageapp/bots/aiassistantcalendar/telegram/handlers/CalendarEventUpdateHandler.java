package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.ActionKnot;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.CalendarEventAiResponse;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.ActionKnotService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserGoogleCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import com.kuklin.manageapp.common.services.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class CalendarEventUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final CalendarService calendarService;
    private final ActionKnotService actionKnotService;
    private final TelegramService telegramService;
    private final TokenService tokenService;
    private final TelegramAiAssistantCalendarBotKeyComponents components;
    private final UserGoogleCalendarService userGoogleCalendarService;
    private static final String VOICE_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать голосовое сообщение";
    private static final String VOICE_DURATION_ERROR_MESSAGE =
            "Ошибка! Голосовое сообщение слишком долгое!";
    private static final String ERROR_MESSAGE =
            "Не получилось добавить мероприятие в календарь!";
    private static final String TEXT_TO_LONG_ERROR_MESSAGE =
            "Ваше сообщение слишком длинное!";
    private static final String EVENT_NOT_FOUND_ERROR_MESSAGE =
            "Не получилось найти собитие";
    private static final String GOOGLE_AUTH_ERROR_MESSAGE =
            "Вам нужно пройти авторизацию заново!";
    private static final String GOOGLE_OTHER_ERROR_MESSAGE =
            "Попробуйте обратиться позже!";
    private static final String CALENDAR_NOT_SET_ERROR_MESSAGE =
            "Вам необходимо установить календарь!";

    private static final Locale RU = new Locale("ru");
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'года', HH:mm", RU);
    private static final DateTimeFormatter DATE_ONLY_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", RU);

    private static final int MAX_VOICE_SECONDS = 60;
    private static final int MAX_TEXT_CHARS = 2000;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();

        //Проверка на количество символов в текстовом сообщении
        int l = message.getText().length();
        if (message.getText() != null && message.getText().length() > MAX_TEXT_CHARS) {
            assistantTelegramBot.sendReturnedMessage(chatId, TEXT_TO_LONG_ERROR_MESSAGE);
            return;
        }

        String request = message.hasVoice() ? processVoiceMessageOrSendError(message) : message.getText();
        if (request == null) return;

        ActionKnot actionKnot = actionKnotService.getActionKnotOrNull(request);
        try {
            if (actionKnot.getAction() == ActionKnot.Action.EVENT_ADD) {
                CalendarEventAiResponse calendarRequest = actionKnot.getCalendarEventAiResponse();

                Event event = calendarService.addEventInCalendar(
                        calendarRequest, telegramUser.getTelegramId());
                sendEventMessage(chatId, event);

            } else if (actionKnot.getAction() == ActionKnot.Action.EVENT_DELETE) {
                List<Event> eventsForRemoving = calendarService
                        .findEventsToRemoveForNextYear(
                                actionKnot, telegramUser.getTelegramId());

                if (eventsForRemoving == null) {
                    assistantTelegramBot.sendReturnedMessage(chatId, CALENDAR_NOT_SET_ERROR_MESSAGE);
                    return;
                }
                if (eventsForRemoving.isEmpty()) {
                    assistantTelegramBot.sendReturnedMessage(chatId, EVENT_NOT_FOUND_ERROR_MESSAGE);
                    return;
                }

                eventsForRemoving.forEach(event -> {
                    sendEventMessage(chatId, event);
                    ThreadUtil.sleep(100);
                });
            } else if (actionKnot.getAction() == ActionKnot.Action.EVENT_EDIT) {
                String eventId = calendarService.findEventIdForEditInYear(
                        telegramUser.getTelegramId(), actionKnot);

                if (eventId == null) {
                    assistantTelegramBot.sendReturnedMessage(chatId, CALENDAR_NOT_SET_ERROR_MESSAGE);
                    return;
                }
                if (eventId.isEmpty() || eventId.isBlank()) {
                    assistantTelegramBot.sendReturnedMessage(chatId, EVENT_NOT_FOUND_ERROR_MESSAGE);
                    return;
                }

                ActionKnot newActionKnot = actionKnotService.getActionKnotForEditMessageOrNull(request);

                Event event = calendarService.editEventInCalendar(eventId, newActionKnot, telegramUser.getTelegramId());
                sendEventMessage(chatId, event);
            }
        } catch (IOException e) {
            log.error(ERROR_MESSAGE, e);
            assistantTelegramBot.sendReturnedMessage(chatId, ERROR_MESSAGE);
        } catch (TokenRefreshException e) {
            if (e.getReason().equals(TokenRefreshException.Reason.INVALID_GRANT)) {
                assistantTelegramBot.sendReturnedMessage(chatId, GOOGLE_AUTH_ERROR_MESSAGE);
            } else {
                assistantTelegramBot.sendReturnedMessage(chatId, GOOGLE_OTHER_ERROR_MESSAGE);
            }
        }

    }

    private void sendEventMessage(Long chatId, Event event) {
        assistantTelegramBot.sendReturnedMessage(
                chatId,
                getResponseString(event),
                getInlineDeleteMessage(event.getId()),
                null
        );
    }

    public static String getResponseString(Event event) {

        StringBuilder stringBuilder = new StringBuilder();
        String description = event.getDescription() != null
                ? event.getDescription()
                : "Не указано";

        stringBuilder.append("<b>ID:</b> ").append(event.getId()).append("\n");
        stringBuilder.append("<b>Мероприятие:</b> ").append(event.getSummary()).append("\n");
        stringBuilder.append("<b>Описание:</b> ").append(description).append("\n");
        stringBuilder.append("<b>Начало:</b> ").append(formatHumanReadable(event.getStart())).append("\n");
        stringBuilder.append("<b>Конец:</b> ").append(formatHumanReadable(event.getEnd()));

        return stringBuilder.toString();
    }

    private static String formatHumanReadable(EventDateTime eventDateTime) {
        if (eventDateTime.getDateTime() != null) {
            String rfc = eventDateTime.getDateTime().toStringRfc3339(); // безопаснее, чем toString()
            OffsetDateTime odt = OffsetDateTime.parse(rfc);
            return odt.format(DATE_TIME_FMT) + " (" + odt.getOffset().toString() + ")";
        }

        // all-day event (dateTime == null, используем date)
        if (eventDateTime.getDate() != null) {
            LocalDate ld = LocalDate.parse(eventDateTime.getDate().toStringRfc3339()); // "YYYY-MM-DD"
            return ld.format(DATE_ONLY_FMT) + " · весь день";
        }

        return "—";
    }

    private String processVoiceMessageOrSendError(Message message) {
        Long chatId = message.getChatId();

        //Проверка длительности голосового сообщения
        Integer duration = message.getVoice().getDuration();
        if (duration != null && duration > MAX_VOICE_SECONDS) {
            // Сообщаем пользователю и выходим
            assistantTelegramBot.sendReturnedMessage(chatId, VOICE_DURATION_ERROR_MESSAGE);
            return null;
        }

        String request = convertVoiceToText(message);

        if (request == null) {
            assistantTelegramBot.sendReturnedMessage(chatId, VOICE_ERROR_MESSAGE);
        }

        log.info(request);
        return request;
    }

    private String convertVoiceToText(Message message) {
        log.info("Скачивание аудиофайла с телеграмма...");
        String fileId = message.getVoice().getFileId();
        byte[] inputAudioFile = telegramService.downloadFileOrNull(assistantTelegramBot, fileId);
        if (inputAudioFile == null) {
            log.info("Аудиофайла не существует.");
            return null;
        }
        return openAiProviderProcessor.fetchAudioResponse(components.getAiKey(), inputAudioFile);
    }

    public static InlineKeyboardMarkup getInlineDeleteMessage(String eventId) {
        String callbackData = Command.ASSISTANT_DELETE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + eventId;
        String buttonText = "Удалить";

        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setCallbackData(callbackData);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(Collections.singletonList(button)));

        return markup;
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_VOICE.getCommandText();
    }
}
