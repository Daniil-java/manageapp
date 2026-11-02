package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.components.services.DayCardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.InviteLinks;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «📸 Поделиться днём» (экран «Сегодня», прошлые дни в истории, предложение после вечернего отчёта):
 * карточка дня картинкой. День — в callback_data ({@link #buildCallbackData}).
 * Подпись — с реферальной ссылкой: при пересылке фото подпись уходит вместе с ним, кнопки — нет.
 * Рендер тяжёлый (PDF → PNG): пока карточка рисуется, повторное нажатие только продлевает «отправляет фото…»;
 * карточку того же дня раньше {@link #COOLDOWN} не перерисовываем — отвечаем, что она уже выше.
 */
@Component
@RequiredArgsConstructor
public class DayCardCalorieUpdateHandler implements CalorieBotUpdateHandler {

    static final Duration COOLDOWN = Duration.ofSeconds(15);
    private static final String DAY_CMD = Command.CALORIE_DAY_CARD.getCommandText() + TelegramBot.DEFAULT_DELIMETER;

    private final CalorieTelegramBot calorieTelegramBot;
    private final DayCardService dayCardService;
    private final UserSettingsService userSettingsService;
    private final ReferralRewardService referralRewardService;
    private final InviteLinks inviteLinks;
    private final CalorieTexts texts;
    // Чьи карточки рисуются прямо сейчас: нажатия приходят параллельно (пул обработки апдейтов)
    private final Set<Long> rendering = ConcurrentHashMap.newKeySet();
    // Последняя отправленная карточка — в памяти: после перезапуска можно нажать сразу
    private final Map<Long, SentCard> lastCards = new ConcurrentHashMap<>();

    record SentCard(LocalDate date, Instant at) {
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) {
            return;
        }
        Long appUserId = telegramUser.getAppUserId();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        if (!rendering.add(appUserId)) {
            calorieTelegramBot.sendChatAction(chatId, ActionType.UPLOADPHOTO);
            return;
        }
        try {
            LocalDate today = LocalDate.now(userSettingsService.getZoneId(appUserId));
            LocalDate date = getDayOrToday(update.getCallbackQuery().getData(), today);
            SentCard last = lastCards.get(appUserId);
            if (last != null && last.date().equals(date) && last.at().plus(COOLDOWN).isAfter(Instant.now())) {
                calorieTelegramBot.sendReturnedMessage(chatId, texts.get(appUserId, BotTextKeys.CARD_ALREADY_SENT));
                return;
            }
            calorieTelegramBot.sendChatAction(chatId, ActionType.UPLOADPHOTO);
            String referralUrl = inviteLinks.getReferralUrl(appUserId);
            int bonusDays = referralRewardService.getPlanDays(ReferralRewardService.INVITED_PLAN_CODE);
            DayCardService.DayCard card = dayCardService.buildCard(appUserId, date, referralUrl, bonusDays);
            switch (card.status()) {
                case EMPTY -> calorieTelegramBot.sendReturnedMessage(chatId, texts.get(appUserId, BotTextKeys.CARD_EMPTY));
                case FAILED -> calorieTelegramBot.sendReturnedMessage(chatId, texts.get(appUserId, BotTextKeys.CARD_FAILED));
                case OK -> {
                    calorieTelegramBot.sendPhotoMessage(chatId, card.png(), "my-day.png",
                            texts.get(appUserId, BotTextKeys.CARD_CAPTION, referralUrl, bonusDays),
                            TelegramKeyboard.builder().row(inviteLinks.buildShareButton(appUserId)).build());
                    lastCards.put(appUserId, new SentCard(date, Instant.now()));
                }
            }
        } finally {
            rendering.remove(appUserId);
        }
    }

    /** Кнопка карточки за день date: "daycard 2026-10-21" — старое сообщение делится своим днём, а не сегодняшним. */
    public static String buildCallbackData(LocalDate date) {
        return DAY_CMD + date;
    }

    /** День из кнопки; без даты (старые кнопки), испорченная или из будущего — сегодня. */
    public static LocalDate getDayOrToday(String data, LocalDate today) {
        if (data == null || !data.startsWith(DAY_CMD)) {
            return today;
        }
        try {
            LocalDate date = LocalDate.parse(data.substring(DAY_CMD.length()));
            return date.isAfter(today) ? today : date;
        } catch (DateTimeParseException e) {
            return today;
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_DAY_CARD.getCommandText();
    }
}
