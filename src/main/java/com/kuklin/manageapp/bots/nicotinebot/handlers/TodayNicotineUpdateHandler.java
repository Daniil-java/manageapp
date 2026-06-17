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
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.kuklin.manageapp.bots.nicotinebot.handlers.StartNicotineUpdateHandler.getKeyboard;

@Component
@RequiredArgsConstructor
public class TodayNicotineUpdateHandler implements NicotineUpdateHandler {
    private final NicotineTelegramBot nicotineTelegramBot;
    private final SmokingRecordService smokingRecordService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {

        Instant now = Instant.now();
        Instant dayAgo = now.minus(24, ChronoUnit.HOURS);

        List<SmokingRecord> smokingRecords = smokingRecordService.getByPeriod(telegramUser.getId(), dayAgo, now);

        nicotineTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                formatEvents(smokingRecords),
                getKeyboard(),
                null
        );
    }

    public static String formatEvents(List<SmokingRecord> events) {
        if (events == null || events.isEmpty()) {
            return "🚬 <b>За период:</b> 0\n<i>Нет записей</i>";
        }

        String body = events.stream()
                .sorted(Comparator.comparing(SmokingRecord::getSmokedAt)) // старые сверху
                .map(event -> "🕓 <code>" + SmokingRecord.format(
                        event.getSmokedAt(),
                        ZoneId.of("Asia/Ho_Chi_Minh")
                ) + "</code>")
                .collect(Collectors.joining("\n"));

        return new StringBuilder()
                .append("🚬 <b>За период:</b> ").append(events.size()).append("\n\n")
                .append(body)
                .toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.NICOTINE_TODAY.getCommandText();
    }
}
