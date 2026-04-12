package com.kuklin.manageapp.bots.nicotinebot.handlers;

import com.kuklin.manageapp.bots.nicotinebot.NicotineTelegramBot;
import com.kuklin.manageapp.bots.nicotinebot.components.SmokingRecord;
import com.kuklin.manageapp.bots.nicotinebot.components.SmokingRecordService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.ZoneId;

import static com.kuklin.manageapp.bots.nicotinebot.handlers.StartNicotineUpdateHandler.getKeyboard;

@Component
@RequiredArgsConstructor
public class MakeNicotineUpdateHandler implements NicotineUpdateHandler {
    private final NicotineTelegramBot nicotineTelegramBot;
    private final SmokingRecordService smokingRecordService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        SmokingRecord smokingRecord = smokingRecordService.create(telegramUser.getId());

        nicotineTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                "Ну кури, пес.\n" + SmokingRecord.format(smokingRecord.getSmokedAt(), ZoneId.of("Asia/Ho_Chi_Minh")),
                getKeyboard(),
                null
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.NICOTINE_MAKE_NEW.getCommandText();
    }
}
