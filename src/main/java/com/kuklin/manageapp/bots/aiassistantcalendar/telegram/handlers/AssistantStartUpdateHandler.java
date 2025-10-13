package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantStartUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private static final String START_MESSAGE =
            """
                    👋 Добро пожаловать!
                    Это прототип бота-ассистента для интеграции с Google Календарём. Перед началом работы нужно выполнить небольшую настройку.
                    ⚙️ Настройка
                    - Укажи идентификатор календаря командой:
                    /set calendarId
                    - Найти его можно в настройках Google Календаря → вкладка «Интеграция календаря».
                    - Дай боту доступ на изменение событий.
                    В настройках календаря добавь этот адрес:
                    tgbot-calendar-assistante@tg-bot-assistent-calendar.iam.gserviceaccount.com
                    📅 Добавление событий
                    Просто опиши мероприятие в свободной форме:
                    - укажи название,
                    - время начала,
                    - и продолжительность.
                    Бот автоматически создаст событие в твоём календаре.
                    Пример:Завтра у меня будет встреча в 3 часа дня, длительностью 2 часа
                    📖 Дополнительные команды
                    - /today — показать расписание на сегодня
                    - Можно отправлять голосовые сообщения\s
                                        
                    """;
    private final TelegramService telegramService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        log.info(update.toString());
        assistantTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), START_MESSAGE);
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_START.getCommandText();
    }
}
