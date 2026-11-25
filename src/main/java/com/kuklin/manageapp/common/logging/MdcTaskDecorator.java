package com.kuklin.manageapp.common.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Переносит контекст логов (MDC) в поток пула: @Async-задача, запущенная при обработке апдейта
 * (событие после оплаты, вебхук), пишет в лог с тем же «[бот tg=… upd=…]», что и запустивший её поток.
 * <p>
 * Снимок MDC берётся в потоке, который ставит задачу; в потоке пула он ставится перед задачей
 * и убирается после — поток пула переиспользуется и не должен унести контекст в следующую задачу.
 * Spring Boot сам применяет бин TaskDecorator к общему пулу @Async; пулу апдейтов — BotUpdateExecutorConfig.
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (context == null) MDC.clear();
            else MDC.setContextMap(context);
            try {
                runnable.run();
            } finally {
                if (previous == null) MDC.clear();
                else MDC.setContextMap(previous);
            }
        };
    }
}
