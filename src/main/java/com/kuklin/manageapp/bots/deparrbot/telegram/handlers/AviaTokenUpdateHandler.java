package com.kuklin.manageapp.bots.deparrbot.telegram.handlers;

import com.kuklin.manageapp.bots.deparrbot.services.AviaApiTokenService;
import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/** /token — выдаёт личный токен для REST API (виджет). Повторный вызов отзывает старый токен. */
@Component
@RequiredArgsConstructor
public class AviaTokenUpdateHandler implements AviaUpdateHandler {
    private static final String MESSAGE = """
            Твой токен для виджета (заголовок X-Avia-Token):

            <code>%s</code>

            Никому его не показывай. Новый /token отключит этот.""";

    private final AviaApiTokenService aviaApiTokenService;
    private final AviaTelegramBot aviaTelegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        String token = aviaApiTokenService.issueToken(telegramUser.getTelegramId());
        aviaTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), String.format(MESSAGE, token));
    }

    @Override
    public String getHandlerListName() {
        return Command.AVIA_TOKEN.getCommandText();
    }
}
