package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.common.configurations.BotUpdateExecutorConfig;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncService {

    // Свой ограниченный пул (BotUpdateExecutorConfig): очередь переполнена — TaskRejectedException вызывающему
    @Async(BotUpdateExecutorConfig.BOT_UPDATE_EXECUTOR)
    public void executeAsyncCustom(TelegramBot telegramBot, Update update, Consumer<Update> runnable) {
        try {
            runnable.accept(update);
        } finally {
            telegramBot.notifyAsyncDone(update);
        }
    }
}
