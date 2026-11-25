package com.kuklin.manageapp.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контекст логов переезжает в поток пула вместе с задачей и не остаётся в нём после задачи.
 */
class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();
    // Один поток: вторая задача гарантированно выполнится в том же потоке, что и первая
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        MDC.clear();
        pool.shutdownNow();
    }

    @Test
    void contextMovesToPoolThreadAndIsClearedAfter() throws Exception {
        AtomicReference<String> inTask = new AtomicReference<>();
        AtomicReference<String> inNextTask = new AtomicReference<>("not run");

        MDC.put(LogContext.KEY, "[CALORIE_BOT tg=1 upd=2]");
        pool.submit(decorator.decorate(() -> inTask.set(MDC.get(LogContext.KEY)))).get();
        MDC.clear();
        // следующая задача без контекста в том же потоке — чужой контекст не унаследован
        pool.submit(() -> inNextTask.set(MDC.get(LogContext.KEY))).get();

        assertThat(inTask.get()).isEqualTo("[CALORIE_BOT tg=1 upd=2]");
        assertThat(inNextTask.get()).isNull();
    }
}
