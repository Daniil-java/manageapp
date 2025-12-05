package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.ActionKnot;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.CalendarEventAiResponse;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.ActionKnotService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
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
    private final UserMessagesLogService userMessagesLogService;
    private final TelegramAiAssistantCalendarBotKeyComponents components;
    private static final String VOICE_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать голосовое сообщение";
    private static final String VOICE_DURATION_ERROR_MESSAGE =
            "Ошибка! Голосовое сообщение слишком долгое!";
    private static final String ERROR_MESSAGE =
            "Не получилось выполнить действие";
    private static final String TEXT_TO_LONG_ERROR_MESSAGE =
            "Ваше сообщение слишком длинное!";
    private static final String EVENT_NOT_FOUND_ERROR_MESSAGE =
            "Не получилось найти событие";
    private static final String GOOGLE_AUTH_ERROR_MESSAGE =
            "Вам нужно пройти авторизацию заново!";
    private static final String GOOGLE_OTHER_ERROR_MESSAGE =
            "Попробуйте обратиться позже!";
    private static final String CALENDAR_NOT_SET_ERROR_MESSAGE =
            "Вам необходимо установить календарь!";
    private static final Locale RU = new Locale("ru");
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("d MMMM, HH:mm", RU);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FMT =
            DateTimeFormatter.ofPattern("d MMMM", RU);
    private static final DateTimeFormatter DAY_OF_WEEK_FMT =
            DateTimeFormatter.ofPattern("EEE", RU);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM", RU);

    private static final int MAX_VOICE_SECONDS = 60;
    private static final int MAX_TEXT_CHARS = 2000;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        assistantTelegramBot.sendChatActionTyping(chatId);


        //Проверка на количество символов в текстовом сообщении
        if (message.getText() != null && message.getText().length() > MAX_TEXT_CHARS) {
            assistantTelegramBot.sendReturnedMessage(chatId, TEXT_TO_LONG_ERROR_MESSAGE);
            userMessagesLogService.createLog(
                    telegramUser.getTelegramId(),
                    telegramUser.getUsername(),
                    telegramUser.getFirstname(),
                    telegramUser.getLastname(),
                    update.getMessage().getText()
            );
            return;
        }

        String request = message.hasVoice()
                ? processVoiceMessageOrSendError(message)
                : message.getText();
        if (request == null) return;

        userMessagesLogService.createLog(
                telegramUser.getTelegramId(),
                telegramUser.getUsername(),
                telegramUser.getFirstname(),
                telegramUser.getLastname(),
                request
        );

        try {
            CalendarService.CalendarContext calendarContext =
                    calendarService.getCalendarContext(telegramUser.getTelegramId());

            if (!checkAuthOrCalendar(calendarContext, chatId)) return;

            String tz = calendarService.getTimeZoneInCalendarOrNull(calendarContext);
            ActionKnot actionKnot = actionKnotService.getActionKnotOrNull(request, tz);
            if (actionKnot.getAction().equals(ActionKnot.Action.ERROR)) {
                assistantTelegramBot.sendReturnedMessage(chatId, "Кажется это сообщение было случайным");
                return;
            }

            if (actionKnot.getAction() == ActionKnot.Action.EVENT_ADD) {
                CalendarEventAiResponse calendarRequest = actionKnot.getCalendarEventAiResponse();

                Event event = calendarService.addEventInCalendar(
                        calendarContext, calendarRequest, telegramUser.getTelegramId());
                assistantTelegramBot.sendReturnedMessage(
                        chatId,
                        getResponseEventString(event),
                        getInlineDeleteMessage(event.getId()),
                        null
                );

            } else if (actionKnot.getAction() == ActionKnot.Action.EVENT_DELETE) {
                List<Event> eventsForRemoving = calendarService
                        .findEventsToRemoveForNextYear(
                                actionKnot, telegramUser.getTelegramId(), tz);

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

                Event oldEvent = calendarService.getEventById(calendarContext, eventId);
                String reply = message.getReplyToMessage() != null
                        ? "\n Сообщение на которое ссылается пользоваетль: " + message.getReplyToMessage().getText()
                        : "";
                ActionKnot newActionKnot = actionKnotService
                        .getActionKnotForEditMessageOrNull(request + reply, oldEvent);
                Event event = calendarService.editEventInCalendar(calendarContext, eventId, newActionKnot);
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

    private boolean checkAuthOrCalendar(CalendarService.CalendarContext context, Long chatId) {
        if (context.getAccessToken() == null) {

            if (context.getCalendarId() == null) {
                assistantTelegramBot.sendReturnedMessage(chatId,
                        "Вам нужно авторизоваться в гугл календаре. Для этого  отправьте свою почту админу @kuklin_daniil");
                return false;
            }
        } else {

            if (context.getCalendarId() == null) {
                assistantTelegramBot.sendReturnedMessage(chatId,
                        "Вам нужно выбрать свой календарь. \nИспользуйте комманду: "
                                + Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText());
                return false;
            }
        }
        return true;
    }

    private void sendEventMessage(Long chatId, Event event) {
        assistantTelegramBot.sendReturnedMessage(
                chatId,
                getResponseEventString(event),
                getInlineDeleteMessage(event.getId()),
                null
        );
    }

    public static String getResponseEventString(Event event) {
        StringBuilder sb = new StringBuilder();

        String summary = event.getSummary();
        if (summary == null) {summary = "Без названия";}

        String description = event.getDescription();
        if (description.equals("Добавлено через Telegram-бота")) {
            description = "";
        }
        if (!description.equals("Добавлено через Telegram-бота") && description.contains("Добавлено через Telegram-бота")) {
            description = event.getDescription().replace("Добавлено через Telegram-бота", "");
        }

        sb
                .append(summary).append("\n")
                .append("• Дата: ").append(formatHumanReadableDayAndMonth(event.getStart())).append("\n")
                .append("• Время: ").append(formatHumanReadableTimeBetweenStartAndEnd(event.getStart(), event.getEnd())).append("\n")
                .append("• Заметки: ").append(description).append("\n");

        if (event.getReminders() != null && event.getReminders().getOverrides() != null) {
            sb.append("• Напомнить за: ");
            for (EventReminder reminder: event.getReminders().getOverrides()) {
                String time = minutesToReadableTime(reminder.getMinutes());
                sb.append(time).append(" ").append("\n");
            }
        }

        return sb.toString();
    }

    public static String minutesToReadableTime(Integer minutes) {
        if (minutes == null) {
            return null;
        }

        int hrs = minutes / 60;
        int mins = minutes % 60;

        StringBuilder sb = new StringBuilder();

        if (hrs > 0) {
            sb.append(hrs).append(" час ");
        }

        sb.append(mins).append(" минут");

        return sb.toString().trim();
    }

    private static String formatHumanReadableTimeBetweenStartAndEnd(EventDateTime start, EventDateTime end) {
        boolean isAllDayStart = start.getDateTime() == null;
        boolean isAllDayEnd = end.getDateTime() == null;

        // ----- Весь день -----
        if (isAllDayStart || isAllDayEnd) {
            return "Весь день";
        }

        // ----- Обычные события с временем -----
        OffsetDateTime startOdt = OffsetDateTime.parse(start.getDateTime().toStringRfc3339());
        OffsetDateTime endOdt = OffsetDateTime.parse(end.getDateTime().toStringRfc3339());

        // Событие в пределах одного дня
        if (startOdt.toLocalDate().equals(endOdt.toLocalDate())) {
            return String.format("%s - %s",
                    startOdt.format(TIME_FMT),
                    endOdt.format(TIME_FMT));
        }

        // Событие на разные даты
        return String.format("%s - %s",
                startOdt.format(DATE_TIME_FMT),
                endOdt.format(DATE_TIME_FMT));
    }

    private static String formatHumanReadableDayAndMonth(EventDateTime eventDateTime) {
        LocalDate date;

        if (eventDateTime.getDateTime() != null) {
            // событие со временем
            OffsetDateTime odt = OffsetDateTime.parse(eventDateTime.getDateTime().toStringRfc3339());
            date = odt.toLocalDate();
        } else if (eventDateTime.getDate() != null) {
            // событие на весь день (без времени)
            date = LocalDate.parse(eventDateTime.getDate().toStringRfc3339());
        } else {
            return "—"; // нет даты вообще
        }

        // Форматируем день недели с заглавной буквы
        String dayOfWeek = date.format(DAY_OF_WEEK_FMT);
        dayOfWeek = dayOfWeek.substring(0, 1).toUpperCase(RU) + dayOfWeek.substring(1);

        // Формируем "(Пн) 6 ноября"
        String datePart = date.format(DATE_FMT);

        return String.format("(%s) %s", dayOfWeek, datePart);
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
        return openAiProviderProcessor.fetchAudioResponse(
                components.getAiKey(), inputAudioFile,
                BotIdentifier.ASSISTANT_BOT, null
        );
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
