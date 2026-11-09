package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InviteLinks;
import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InvitePromptSender;
import com.kuklin.manageapp.bots.caloriebot.subscription.telegram.SubscriptionTexts;
import com.kuklin.manageapp.bots.caloriebot.web.CalorieSiteLinks;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.keyboard.ButtonStyle;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Arrays;
import java.util.Optional;

/**
 * /intro — знакомство с ботом одним сообщением-меню: разделы открываются на месте этого сообщения,
 * «← К разделам» возвращает меню. Кнопки действий в разделах (настройки, подписка, профиль…) открывают
 * обычные экраны бота новым сообщением. Меню же показывает «Далее» под старыми приветствиями
 * (StartFlowCalorieUpdateHandler) и кнопка «Знакомство» в /help.
 * Новичку знакомство приходит по частям: /start — как записать первое блюдо, остальное — после него
 * (FirstDishFollowUpSender).
 */
@Component
@RequiredArgsConstructor
public class IntroCalorieUpdateHandler implements CalorieBotUpdateHandler {
    static final String CHANNEL_URL = "https://t.me/zephyr_ai";
    /** callbackData меню разделов. */
    public static final String HOME_DATA = Command.CALORIE_INTRO.getCommandText() + TelegramBot.DEFAULT_DELIMETER + "home";

    @Getter
    @RequiredArgsConstructor
    enum Section {
        FOOD("food", BotTextKeys.INTRO_BUTTON_FOOD),
        PROFILE("profile", BotTextKeys.INTRO_BUTTON_PROFILE),
        REMINDERS("reminders", BotTextKeys.INTRO_BUTTON_REMINDERS),
        REPORTS("reports", BotTextKeys.INTRO_BUTTON_REPORTS),
        PREMIUM("premium", BotTextKeys.INTRO_BUTTON_PREMIUM),
        REWARDS("rewards", BotTextKeys.INTRO_BUTTON_REWARDS),
        CHANNEL("channel", BotTextKeys.INTRO_BUTTON_CHANNEL);

        private final String code;
        private final String buttonKey;

        String buildCallbackData() {
            return Command.CALORIE_INTRO.getCommandText() + TelegramBot.DEFAULT_DELIMETER + code;
        }

        static Optional<Section> findByCode(String code) {
            return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst();
        }
    }

    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;
    private final InvitePromptSender invitePromptSender;
    private final InviteLinks inviteLinks;
    private final SubscriptionTexts subscriptionTexts;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        if (update.hasMessage()) {
            sendIntro(update.getMessage().getChatId(), appUserId);
            return;
        }
        if (!update.hasCallbackQuery()) return;

        MaybeInaccessibleMessage message = update.getCallbackQuery().getMessage();
        String[] parts = update.getCallbackQuery().getData().split(TelegramBot.DEFAULT_DELIMETER);
        Optional<Section> section = parts.length > 1 ? Section.findByCode(parts[1]) : Optional.empty();
        if (section.isPresent()) {
            calorieTelegramBot.sendEditMessage(message.getChatId(), buildSectionText(appUserId, section.get()),
                    message.getMessageId(), buildSectionKeyboard(appUserId, section.get()));
        } else {
            calorieTelegramBot.sendEditMessage(message.getChatId(), buildHomeText(appUserId),
                    message.getMessageId(), buildHomeKeyboard(appUserId));
        }
    }

    /** Меню разделов новым сообщением. */
    public void sendIntro(Long chatId, Long appUserId) {
        calorieTelegramBot.sendReturnedMessage(chatId, buildHomeText(appUserId), buildHomeKeyboard(appUserId), null);
    }

    String buildHomeText(Long appUserId) {
        return texts.get(appUserId, BotTextKeys.INTRO_HOME);
    }

    InlineKeyboardMarkup buildHomeKeyboard(Long appUserId) {
        boolean profileFilled = isProfileFilled(appUserId);
        String profileLabel = texts.get(appUserId, Section.PROFILE.getButtonKey())
                + (profileFilled ? "" : " ⚠️");
        return TelegramKeyboard.builder()
                .row(buildSectionButton(appUserId, Section.FOOD))
                .row(TelegramKeyboard.button(profileLabel, Section.PROFILE.buildCallbackData()))
                .row(buildSectionButton(appUserId, Section.REMINDERS), buildSectionButton(appUserId, Section.REPORTS))
                .row(buildSectionButton(appUserId, Section.PREMIUM), buildSectionButton(appUserId, Section.REWARDS))
                .row(buildSectionButton(appUserId, Section.CHANNEL),
                        TelegramKeyboard.webAppButton(texts.get(appUserId, BotTextKeys.INTRO_BUTTON_FAQ),
                                CalorieSiteLinks.INSTRUCTION_URL))
                .row(menuButtons.buildCloseButton(appUserId))
                .build();
    }

    String buildSectionText(Long appUserId, Section section) {
        return switch (section) {
            case FOOD -> texts.get(appUserId, BotTextKeys.INTRO_FOOD);
            case PROFILE -> texts.get(appUserId, isProfileFilled(appUserId)
                    ? BotTextKeys.INTRO_PROFILE_FILLED : BotTextKeys.INTRO_PROFILE_EMPTY);
            case REMINDERS -> texts.get(appUserId, BotTextKeys.INTRO_REMINDERS);
            case REPORTS -> texts.get(appUserId, BotTextKeys.INTRO_REPORTS);
            case PREMIUM -> subscriptionTexts.buildStatusLine(appUserId) + "\n\n" + texts.get(appUserId, BotTextKeys.PAYMENT_PLANS);
            case REWARDS -> invitePromptSender.buildInviteRewardsText(appUserId);
            case CHANNEL -> texts.get(appUserId, BotTextKeys.START_CHANNEL);
        };
    }

    InlineKeyboardMarkup buildSectionKeyboard(Long appUserId, Section section) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        switch (section) {
            case FOOD -> builder.row(buildActionButton(appUserId, BotTextKeys.BUTTON_TODAY, Command.CALORIE_TODAY_LIST));
            case PROFILE -> builder.row(isProfileFilled(appUserId)
                    ? buildActionButton(appUserId, BotTextKeys.BUTTON_PROFILE, Command.CALORIE_PROFILE)
                    : buildActionButton(appUserId, BotTextKeys.START_FLOW_BUTTON_FILL_PROFILE, Command.CALORIE_PROFILE_DIALOGUE));
            case REMINDERS -> builder.row(buildActionButton(appUserId, BotTextKeys.BUTTON_SETTINGS, Command.CALORIE_SETTINGS));
            case REPORTS -> builder.row(buildActionButton(appUserId, BotTextKeys.BUTTON_REPORT, Command.CALORIE_REPORT));
            case PREMIUM -> builder.row(buildActionButton(appUserId, BotTextKeys.BUTTON_SUBSCRIPTION, Command.CALORIE_PAYMENT_PAYLOAD_PLAN));
            case REWARDS -> builder.row(inviteLinks.buildShareButton(appUserId)).row(inviteLinks.buildDetailsButton(appUserId));
            case CHANNEL -> builder.row(TelegramKeyboard.urlButton(
                    texts.get(appUserId, BotTextKeys.INTRO_BUTTON_OPEN_CHANNEL), CHANNEL_URL, ButtonStyle.PRIMARY));
        }
        return builder
                .row(TelegramKeyboard.button(texts.get(appUserId, BotTextKeys.INTRO_BUTTON_BACK), HOME_DATA))
                .build();
    }

    private boolean isProfileFilled(Long appUserId) {
        return userNutritionProfileService.getOrCreateProfile(appUserId).checkTargetCalculateParams();
    }

    private InlineKeyboardButton buildSectionButton(Long appUserId, Section section) {
        return TelegramKeyboard.button(texts.get(appUserId, section.getButtonKey()), section.buildCallbackData());
    }

    private InlineKeyboardButton buildActionButton(Long appUserId, String textKey, Command command) {
        return TelegramKeyboard.button(texts.get(appUserId, textKey), command.getCommandText(), ButtonStyle.PRIMARY);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_INTRO.getCommandText();
    }
}
