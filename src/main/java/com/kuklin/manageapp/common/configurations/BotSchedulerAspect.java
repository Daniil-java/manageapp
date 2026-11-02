package com.kuklin.manageapp.common.configurations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Не запускает шедулеры выключенного бота: перехватывает методы с @Scheduled
 * в классах с {@link BotScheduler} и пропускает вызов, если бот выключен в {@link EnabledBots}.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class BotSchedulerAspect {

    private final EnabledBots enabledBots;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) && @within(botScheduler)")
    public Object skipIfBotDisabled(ProceedingJoinPoint joinPoint, BotScheduler botScheduler) throws Throwable {
        if (!enabledBots.isEnabled(botScheduler.value())) {
            log.debug("Skip scheduler {} — bot {} is disabled",
                    joinPoint.getSignature().toShortString(), botScheduler.value());
            return null;
        }
        return joinPoint.proceed();
    }
}
