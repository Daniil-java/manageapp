package com.kuklin.manageapp.common.configurations;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Шедулеры этого класса принадлежат боту value: если бот выключен ({@link EnabledBots}),
 * его методы с @Scheduled не выполняются (BotSchedulerAspect).
 * <p>
 * Методы с @Scheduled в таком классе должны быть public: класс оборачивается прокси,
 * а private-метод прокси вызвал бы на пустом объекте без зависимостей.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BotScheduler {
    BotIdentifier value();
}
