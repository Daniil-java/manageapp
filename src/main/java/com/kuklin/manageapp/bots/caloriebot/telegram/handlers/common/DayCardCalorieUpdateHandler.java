package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.components.services.DayCardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.InviteLinks;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «📸 Поделиться днём» (экран «Сегодня», предложение после вечернего отчёта): карточка дня картинкой.
 * Подпись — с реферальной ссылкой: при пересылке фото подпись уходит вместе с ним, кнопки — нет.
 * Рендер тяжёлый (PDF → PNG), поэтому повторное нажатие раньше {@link #COOLDOWN} игнорируется.
 */
@Component
@RequiredArgsConstructor
public class DayCardCalorieUpdateHandler implements CalorieBotUpdateHandler {

    static final Duration COOLDOWN = Duration.ofSeconds(15);

    private final CalorieTelegramBot calorieTelegramBot;
    private final DayCardService dayCardService;
    private final ReferralRewardService referralRewardService;
    private final InviteLinks inviteLinks;
    private final CalorieTexts texts;
    // Когда пользователь последний раз получил карточку — в памяти: после перезапуска можно нажать сразу
    private final Map<Long, Instant> lastCardAt = new ConcurrentHashMap<>();

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) {
            return;
        }
        Long appUserId = telegramUser.getAppUserId();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Instant now = Instant.now();
        Instant last = lastCardAt.get(appUserId);
        if (last != null && last.plus(COOLDOWN).isAfter(now)) {
            return;
        }
        lastCardAt.put(appUserId, now);

        String referralUrl = inviteLinks.getReferralUrl(appUserId);
        int bonusDays = referralRewardService.getPlanDays(ReferralRewardService.INVITED_PLAN_CODE);
        DayCardService.DayCard card = dayCardService.buildCard(appUserId, referralUrl, bonusDays);
        switch (card.status()) {
            case EMPTY -> calorieTelegramBot.sendReturnedMessage(chatId, texts.get(appUserId, BotTextKeys.CARD_EMPTY));
            case FAILED -> calorieTelegramBot.sendReturnedMessage(chatId, texts.get(appUserId, BotTextKeys.CARD_FAILED));
            case OK -> calorieTelegramBot.sendPhotoMessage(chatId, card.png(), "my-day.png",
                    texts.get(appUserId, BotTextKeys.CARD_CAPTION, referralUrl, bonusDays),
                    TelegramKeyboard.builder().row(inviteLinks.buildShareButton(appUserId)).build());
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_DAY_CARD.getCommandText();
    }
}
