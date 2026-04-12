package com.kuklin.manageapp.bots.nicotinebot.handlers;

import com.kuklin.manageapp.bots.nicotinebot.NicotineTelegramBot;
import com.kuklin.manageapp.bots.nicotinebot.components.SmokingRecord;
import com.kuklin.manageapp.bots.nicotinebot.components.SmokingRecordService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static com.kuklin.manageapp.bots.nicotinebot.handlers.StartNicotineUpdateHandler.getKeyboard;

@Component
@RequiredArgsConstructor
public class LastNicotineUpdateHandler implements NicotineUpdateHandler{
    private final SmokingRecordService smokingRecordService;
    private final NicotineTelegramBot nicotineTelegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Optional<SmokingRecord> optional = smokingRecordService
                .getLastSmokingEvent(telegramUser.getId());

        Long chatId = update.getMessage().getChatId();
        if (optional.isEmpty()) {
            nicotineTelegramBot.sendReturnedMessage(
                    chatId, "Ты ж не курил!",
                    getKeyboard(),
                    null
            );
        } else {
            SmokingRecord record = optional.get();
            nicotineTelegramBot.sendReturnedMessage(
                    chatId, getMessageText(record),
                    getKeyboard(),
                    null
            );
        }
    }

    private String getMessageText(SmokingRecord smokingRecord) {
        StringBuilder sb = new StringBuilder();

        String diff = SmokingRecord.diffHHmm(smokingRecord.getSmokedAt(), Instant.now());
        String emojiTime = toEmojiTime(diff);

        String formattedTime = SmokingRecord.format(
                smokingRecord.getSmokedAt(),
                ZoneId.of("Asia/Ho_Chi_Minh")
        );

        sb.append("🚬 <b>Последняя сигарета</b>\n\n")
                .append("⏱ <b>Прошло:</b> ").append(emojiTime).append("\n")
                .append("🕓 <b>Время:</b> ").append("<code>").append(formattedTime).append("</code>");

        return sb.toString();
    }

    private String toEmojiTime(String time) {
        StringBuilder result = new StringBuilder();

        for (char c : time.toCharArray()) {
            switch (c) {
                case '0': result.append("0️⃣"); break;
                case '1': result.append("1️⃣"); break;
                case '2': result.append("2️⃣"); break;
                case '3': result.append("3️⃣"); break;
                case '4': result.append("4️⃣"); break;
                case '5': result.append("5️⃣"); break;
                case '6': result.append("6️⃣"); break;
                case '7': result.append("7️⃣"); break;
                case '8': result.append("8️⃣"); break;
                case '9': result.append("9️⃣"); break;
                case ':': result.append(" : "); break;
                default: result.append(c);
            }
        }

        return result.toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.NICOTINE_GET_LAST.getCommandText();
    }
}
