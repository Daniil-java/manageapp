package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UserSettingsRepository;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitePromptSenderTest {

    private static final long USER = 7L;
    private static final long CHAT = 1007L;

    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
    private final ReferralRewardService rewardService = mock(ReferralRewardService.class);
    private final UtmService utmService = mock(UtmService.class);
    private InvitePromptSender sender;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(USER)).thenReturn("ru");
        when(settings.getZoneId(USER)).thenReturn(ZoneOffset.UTC);
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), settings);
        when(bot.getBotUsername()).thenReturn("@calorydairy_bot");
        when(utmService.getOrCreateReferralLink(USER)).thenReturn("ref7");
        when(rewardService.getPlanDays(ReferralRewardService.INVITED_PLAN_CODE)).thenReturn(3);
        when(rewardService.getPlanDays(ReferralRewardService.INVITER_PLAN_CODE)).thenReturn(7);
        when(rewardService.getPlanDays(ReferralRewardService.FRIEND_PAID_PLAN_CODE)).thenReturn(30);
        InviteLinks links = new InviteLinks(bot, utmService, rewardService, texts);
        sender = new InvitePromptSender(bot, settingsRepository, settings, rewardService, links, texts);
    }

    private List<InlineKeyboardButton> sentButtons() {
        ArgumentCaptor<ReplyKeyboard> keyboard = ArgumentCaptor.forClass(ReplyKeyboard.class);
        verify(bot).sendReturnedMessage(eq(CHAT), anyString(), keyboard.capture(), isNull());
        return ((InlineKeyboardMarkup) keyboard.getValue()).getKeyboard().stream().flatMap(List::stream).toList();
    }

    @Test
    void startFlowInviteIsSentOnce() {
        when(settingsRepository.claimFirstInvitePrompt(eq(USER), any())).thenReturn(1);

        sender.sendStartFlowInvite(CHAT, USER);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendReturnedMessage(eq(CHAT), text.capture(), any(), isNull());
        assertThat(text.getValue()).contains("+3 дня", "+7 дней", "+30 дней");
        List<InlineKeyboardButton> buttons = sentButtons();
        assertThat(buttons.get(0).getUrl())
                .startsWith("https://t.me/share/url?url=https%3A%2F%2Ft.me%2Fcalorydairy_bot%3Fstart%3Dref7&text=")
                .doesNotContain("+");
    }

    @Test
    void startFlowInviteIsNotRepeated() {
        when(settingsRepository.claimFirstInvitePrompt(eq(USER), any())).thenReturn(0);

        sender.sendStartFlowInvite(CHAT, USER);

        verify(bot, never()).sendReturnedMessage(anyLong(), anyString(), any(), any());
    }

    @Test
    void eveningPromptRespectsWeekAndAccountAge() {
        when(settingsRepository.claimInvitePrompt(eq(USER), any(), any(), any())).thenReturn(1);

        assertThat(sender.sendAfterDailyReportIfDue(CHAT, USER)).isTrue();

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> joinedBefore = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> promptedBefore = ArgumentCaptor.forClass(Instant.class);
        verify(settingsRepository).claimInvitePrompt(eq(USER), now.capture(), joinedBefore.capture(), promptedBefore.capture());
        assertThat(Duration.between(joinedBefore.getValue(), now.getValue())).isEqualTo(InvitePromptSender.MIN_ACCOUNT_AGE);
        assertThat(Duration.between(promptedBefore.getValue(), now.getValue())).isEqualTo(InvitePromptSender.PROMPT_INTERVAL);
        // Две кнопки: карточка за сегодня и «Переслать другу»
        List<InlineKeyboardButton> buttons = sentButtons();
        assertThat(buttons).hasSize(2);
        assertThat(buttons.get(0).getCallbackData()).isEqualTo("daycard " + LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void eveningPromptSkippedWhenTooEarly() {
        when(settingsRepository.claimInvitePrompt(eq(USER), any(), any(), any())).thenReturn(0);

        assertThat(sender.sendAfterDailyReportIfDue(CHAT, USER)).isFalse();
        verify(bot, never()).sendReturnedMessage(anyLong(), anyString(), any(), any());
    }

    @Test
    void eveningPromptFailureIsSwallowed() {
        when(settingsRepository.claimInvitePrompt(eq(USER), any(), any(), any())).thenThrow(new IllegalStateException("db"));

        assertThat(sender.sendAfterDailyReportIfDue(CHAT, USER)).isFalse();
    }
}
