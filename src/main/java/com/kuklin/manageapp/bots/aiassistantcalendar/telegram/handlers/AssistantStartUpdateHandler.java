package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantStartUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private static final String START_MESSAGE =
            """
                    👋 Добро пожаловать!
                                        
                    🗓️ Это бот-ассистент с поддержкой Google Календаря — голосовые и текстовые команды.
                    📝 Просто опиши задачу в свободной форме.
                                        
                    Пример: Завтра у меня встреча в 15:00, длительность 2 часа.
                                        
                    📧 Чтобы авторизоваться через Google, отправьте письмо на почту: example@gmail.com и дождитесь подтверждения. После этого введите команду /auth.
                    🔧 Если хотите подключить календарь вручную (без авторизации) — введите команду /help.
                                        
                    🚀 Удачи — пусть напоминания работают, а вы — нет.
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        assistantTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                START_MESSAGE,
                getAuthButton(),
                null
        );
    }

    public InlineKeyboardMarkup getAuthButton() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        builder.row(TelegramKeyboard.button("Авторизация", Command.ASSISTANT_AUTH.getCommandText()));

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_START.getCommandText();
    }
}
