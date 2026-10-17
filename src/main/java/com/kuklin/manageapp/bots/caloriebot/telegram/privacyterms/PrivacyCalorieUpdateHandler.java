package com.kuklin.manageapp.bots.caloriebot.telegram.privacyterms;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class PrivacyCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    public static final String PRIVACY = """
            Политика конфиденциальности
                        
            Настоящая Политика описывает, как настоящий Бот (далее — Бот) обрабатывает информацию при его использовании в мессенджере Telegram.
                        
            Сбор информации
            Бот обрабатывает следующие типы данных:
                        
            Технические данные: идентификатор пользователя Telegram (User ID), имя пользователя (username).
                        
            Данные профиля: информация, добровольно указанная пользователем (рост, вес, пол, возраст, цели и уровень активности).
                        
            Пользовательский контент: фотографии, текстовые и голосовые сообщения, отправляемые Боту для анализа состава питания.
                        
            Цели и хранение
            Данные используются для обеспечения работы функций Бота. Обработка и хранение данных осуществляются на защищенных серверах, обеспечивающих конфиденциальность и безопасность технической инфраструктуры проекта.
                        
            Передача третьим лицам
            Для обработки медиафайлов и текстов Бот может использовать сторонние API (включая OpenAI). Передаваемые данные используются исключительно для генерации ответа алгоритмами ИИ.
                        
            Использование данных для ИИ
            Владелец оставляет за собой право использовать анонимизированный контент (фото блюд без изображений людей) для улучшения точности алгоритмов и обучения моделей в будущем.
                        
            Удаление данных
            Вы можете прекратить использование Бота и запросить удаление своих данных, обратившись в поддержку через встроенную команду /support.
                        
            Последнее обновление: Март 2026.
            """;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                PRIVACY
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PRIVACY.getCommandText();
    }
}
