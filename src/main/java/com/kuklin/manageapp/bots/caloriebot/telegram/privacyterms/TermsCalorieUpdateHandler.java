package com.kuklin.manageapp.bots.caloriebot.telegram.privacyterms;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class TermsCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    public static final String TERMS =
            """
                    Пользовательское соглашение
                                        
                    Используя настоящий Бот (далее — Бот), вы принимаете условия данного Соглашения.
                                        
                    ОТКАЗ ОТ ОТВЕТСТВЕННОСТИ: Бот не является медицинским сервисом. Все расчеты производятся алгоритмами ИИ и могут содержать погрешности.
                                        
                    Общие положения
                    Бот предоставляет инструменты для автоматизированного учета питания. Весь контент носит информационный характер. Владелец не несет ответственности за решения, принятые пользователем на основе данных Бота.
                                        
                    Медицинский дисклеймер
                    Информация в Боте не заменяет консультацию врача. Перед изменением рациона или режима нагрузок проконсультируйтесь со специалистом. Вы используете сервис на свой страх и риск.
                                        
                    Платежи
                    Оплата дополнительных функций может производиться через инструменты мессенджера Telegram. Возврат средств рассматривается в индивидуальном порядке через команду /support.
                                        
                    Рассылки
                    Пользователь соглашается на получение системных уведомлений и новостей об обновлениях Бота.
                                        
                    Последнее обновление: Март 2026
                    """;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                TERMS
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_TERMS.getCommandText();
    }
}
