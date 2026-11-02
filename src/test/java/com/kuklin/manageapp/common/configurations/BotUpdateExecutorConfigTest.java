package com.kuklin.manageapp.common.configurations;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Свой пул апдейтов не должен отключать общий пул Spring: без spring.task.execution.mode=force
 * Boot 3.5 не создаёт applicationTaskExecutor, как только в контексте есть другой Executor,
 * и прочие @Async (вебхуки платежей) уходили бы мимо него.
 */
class BotUpdateExecutorConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(BotUpdateExecutorConfig.class);

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
