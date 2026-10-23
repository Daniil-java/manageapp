package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Ссылки на бота и кнопки приглашения: экран «💎Пригласить», предложения пригласить, карточка «мой день».
 * <p>
 * «Переслать другу» — url-кнопка t.me/share/url: Telegram сам открывает выбор чата с готовым текстом и ссылкой.
 * Раньше была switch_inline_query — без inline-режима у бота она вставляла в чат «@бот текст».
 */
@Component
@RequiredArgsConstructor
public class InviteLinks {

    private static final String SHARE_URL = "https://t.me/share/url?url=";

    private final CalorieTelegramBot calorieTelegramBot;
    private final UtmService utmService;
    private final ReferralRewardService referralRewardService;
    private final CalorieTexts texts;

    /** https://t.me/бот?start=код */
    public String buildBotUrl(String code) {
        return "https://t.me/" + calorieTelegramBot.getBotUsername().replace("@", "") + "?start=" + code;
    }

    /** Реферальная ссылка пользователя (ref<appUserId>), запись в utm_links создаётся при первом обращении. */
    public String getReferralUrl(Long appUserId) {
        return buildBotUrl(utmService.getOrCreateReferralLink(appUserId));
    }

    /** «🚀 Переслать другу»: выбор чата с текстом приглашения и реферальной ссылкой. */
    public InlineKeyboardButton buildShareButton(Long appUserId) {
        String shareText = texts.get(appUserId, BotTextKeys.REFERRAL_SHARE_TEXT,
                referralRewardService.getPlanDays(ReferralRewardService.INVITED_PLAN_CODE));
        return TelegramKeyboard.urlButton(texts.get(appUserId, BotTextKeys.REFERRAL_BUTTON_SHARE),
                SHARE_URL + encode(getReferralUrl(appUserId)) + "&text=" + encode(shareText), ButtonStyle.PRIMARY);
    }

    /** «💎 Подробнее» — экран приглашения (UtmCalorieUpdateHandler) с условиями и счётчиками. */
    public InlineKeyboardButton buildDetailsButton(Long appUserId) {
        return TelegramKeyboard.button(texts.get(appUserId, BotTextKeys.INVITE_BUTTON_DETAILS),
                Command.CALORIE_UTM.getCommandText());
    }

    /** Для query-параметра: пробелы — %20, а не «+»: так их одинаково понимают все клиенты Telegram. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
