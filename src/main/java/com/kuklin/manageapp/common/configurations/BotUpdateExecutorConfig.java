package com.kuklin.manageapp.common.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Свой пул для апдейтов всех ботов ({@link com.kuklin.manageapp.common.services.AsyncService}).
 * <p>
 * Раньше апдейты шли в общий пул Spring: 8 потоков и бесконечная очередь. Восемь пользователей ждут ИИ
 * (10–30 с) — девятый с простым кликом стоит в очереди, а при наплыве очередь растёт без предела,
 * и бот отвечает через минуты. Теперь потоков больше, очередь ограничена: переполнена — пул отказывает
 * сразу (TaskRejectedException), и {@code TelegramBot.doAsync} отвечает «бот перегружен» вместо долгого молчания.
 * Остальные {@code @Async} (вебхуки платежей) остаются в общем пуле и под этот лимит не попадают.
 */
@Configuration
public class BotUpdateExecutorConfig {
    public static final String BOT_UPDATE_EXECUTOR = "botUpdateExecutor";

    @Bean(name = BOT_UPDATE_EXECUTOR)
    public ThreadPoolTaskExecutor botUpdateExecutor(
            @Value("${bots.update-executor.threads:16}") int threads,
            @Value("${bots.update-executor.queue:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("bot-update-");
        // Остановка приложения: дать начатым апдейтам (ответ ИИ) закончиться
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
