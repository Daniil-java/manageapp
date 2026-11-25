package com.kuklin.manageapp.common.telegram.config;

import com.kuklin.manageapp.common.logging.LogContext;
import com.kuklin.manageapp.common.logging.MdcTaskDecorator;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Свой пул апдейтов не должен отключать общий пул Spring: без spring.task.execution.mode=force
 * Boot 3.5 не создаёт applicationTaskExecutor, как только в контексте есть другой Executor,
 * и прочие @Async (вебхуки платежей) уходили бы мимо него.
 */
class BotUpdateExecutorConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(BotUpdateExecutorConfig.class, MdcTaskDecorator.class);

    // Контекст логов (MDC) переезжает в оба пула: в пул апдейтов — через BotUpdateExecutorConfig,
    // в общий пул @Async — Spring Boot сам применяет бин TaskDecorator
    @Test
    void bothExecutorsCarryLogContext() {
        runner.withPropertyValues("spring.task.execution.mode=force")
                .run(context -> {
                    for (String name : new String[]{"applicationTaskExecutor", BotUpdateExecutorConfig.BOT_UPDATE_EXECUTOR}) {
                        ThreadPoolTaskExecutor executor = context.getBean(name, ThreadPoolTaskExecutor.class);
                        AtomicReference<String> inTask = new AtomicReference<>();
                        MDC.put(LogContext.KEY, "[CALORIE_BOT tg=1 upd=2]");
                        try {
                            executor.submit(() -> inTask.set(MDC.get(LogContext.KEY))).get();
                        } finally {
                            MDC.clear();
                        }
                        assertThat(inTask.get()).as(name).isEqualTo("[CALORIE_BOT tg=1 upd=2]");
                    }
                });
    }

    @Test
    void bothExecutorsExistInForceMode() {
        runner.withPropertyValues("spring.task.execution.mode=force", "bots.update-executor.threads=16",
                        "bots.update-executor.queue=200")
                .run(context -> {
                    assertThat(context).hasBean("applicationTaskExecutor");
                    ThreadPoolTaskExecutor executor =
                            context.getBean(BotUpdateExecutorConfig.BOT_UPDATE_EXECUTOR, ThreadPoolTaskExecutor.class);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(16);
                    assertThat(executor.getQueueCapacity()).isEqualTo(200);
                });
    }

    @Test
    void withoutForceModeSharedExecutorDisappears() {
        // Документирует, зачем в application.yaml spring.task.execution.mode: force
        runner.run(context -> assertThat(context).doesNotHaveBean("applicationTaskExecutor"));
    }
}
