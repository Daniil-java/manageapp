package com.kuklin.manageapp.bots.metrics.telegram;

import com.kuklin.manageapp.bots.metrics.configurations.MetricsBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@Slf4j
public class MetricsTelegramBot extends TelegramBot {

    @Autowired
    private MetricsTelegramFacade metricsTelegramFacade;

    public MetricsTelegramBot(MetricsBotKeyComponents components) {
        super(components.getKey());
    }

    @Override
    public void onUpdateReceived(Update update) {
        metricsTelegramFacade.handleUpdate(update);
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.METRICS;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.METRICS.getBotUsername();
    }
}
