package com.kuklin.manageapp.common.library.tgmodels;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public abstract class TelegramFacade {
    private Map<String, UpdateHandler> updateHandlerMap = new ConcurrentHashMap<>();

    public void register(String command, UpdateHandler updateHandler) {
        if (updateHandlerMap.containsKey(command)) {
            log.error("This command is already exists!");
        }
        updateHandlerMap.put(command, updateHandler);
    }

    public abstract void handleUpdate(Update update);
}
