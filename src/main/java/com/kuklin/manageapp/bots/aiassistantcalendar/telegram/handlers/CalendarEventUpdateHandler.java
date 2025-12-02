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
    private final TelegramAiAssistantCalendarBotKeyComponents components;
    private final UserGoogleCalendarService userGoogleCalendarService;
    private static final String VOICE_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать голосовое сообщение";
    private static final String ERROR_MESSAGE =
            "Не получилось добавить мероприятие в календарь!";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String calendarId = userGoogleCalendarService.getUserCalendarIdByTelegramIdOrNull(telegramUser.getTelegramId());
        if (calendarId == null) {
            assistantTelegramBot.sendReturnedMessage(chatId, SetCalendarIdUpdateHandler.CALENDAR_IS_NULL_MSG);
            return;
        }

        String request = message.hasVoice() ? processVoiceMessage(message) : message.getText();

        ActionKnot actionKnot = actionKnotService.getActionKnotOrNull(request);
        try {
            if (actionKnot.getAction() == ActionKnot.Action.EVENT_ADD) {
                CalendarEventAiResponse calendarRequest = actionKnot.getCalendarEventAiResponse();

                Event event = calendarService.addEventInCalendar(
                        calendarRequest, calendarId);
                sendEventMessage(chatId, event);

            } else if (actionKnot.getAction() == ActionKnot.Action.EVENT_DELETE) {
                List<Event> eventsForRemoving =
                        calendarService.removeEventInCalendarByMessage(calendarId, actionKnot);

                eventsForRemoving.forEach(event -> {
                    sendEventMessage(chatId, event);
                    ThreadUtil.sleep(100);
                });
            }
        } catch (IOException e) {
            log.error(ERROR_MESSAGE, e);
            assistantTelegramBot.sendReturnedMessage(chatId, ERROR_MESSAGE);
        }

    }

    private void sendEventMessage(Long chatId, Event event) {
        assistantTelegramBot.sendReturnedMessage(
                chatId,
                getResponseString(event),
                getInlineMessage(event.getId()),
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
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(eventDateTime.getDateTime().toString());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'года', HH:mm", new Locale("ru"));
        String formatted = offsetDateTime.format(formatter);

        // Добавляем реальный оффсет
        return formatted + " (" + offsetDateTime.getOffset().toString() + ")";
    }

    private String processVoiceMessage(Message message) {
        Long chatId = message.getChatId();
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

    public static InlineKeyboardMarkup getInlineMessage(String eventId) {
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
