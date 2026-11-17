package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.dish.DishService;
import com.kuklin.manageapp.bots.caloriebot.dish.telegram.DishKeyboards;
import com.kuklin.manageapp.bots.caloriebot.dish.telegram.DishTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.onboarding.telegram.SampleDishCalorieUpdateHandler.SampleState;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SampleDishCalorieUpdateHandlerTest {

    private static final Long APP_USER_ID = 1L;
    private static final Long CHAT_ID = 100L;
    private static final int MESSAGE_ID = 55;

    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final UserSettingsService settingsService = mock(UserSettingsService.class);
    private final UserNutritionProfileService profileService = mock(UserNutritionProfileService.class);
    private final CalorieTexts texts = new CalorieTexts(new I18nProperties(), settingsService);
    private final DishTexts dishTexts = new DishTexts(texts, mock(DishService.class), profileService);
    private final SampleDishCalorieUpdateHandler handler = new SampleDishCalorieUpdateHandler(
            bot, dishTexts, new DishKeyboards(texts), texts, settingsService, profileService);

    @BeforeEach
    void setUp() {
        when(profileService.getOrCreateProfile(APP_USER_ID)).thenReturn(new UserNutritionProfile());
    }

    @Test
    void sampleSendsCardWithBarThenProfileCall() {
        handler.handle(buildCallbackUpdate(Command.CALORIE_SAMPLE_DISH.getCommandText()), buildUser());

        verify(settingsService).claimFirstDishSample(eq(APP_USER_ID), any());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ReplyKeyboard> keyboard = ArgumentCaptor.forClass(ReplyKeyboard.class);
        verify(bot, times(2)).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture(), keyboard.capture(), isNull());

        // Карточка: название, шкала по условной норме (760 съедено + 410 = 1170 из 2000), кнопки примера
        assertThat(text.getAllValues().get(0)).contains("Овсянка с бананом и кофе", "1170/2000", "+410", "🟦", "🟪");
        assertThat(getCallbackData(keyboard.getAllValues().get(0)))
                .allMatch(data -> data.equals(TelegramKeyboard.NO_ACTION)
                        || data.startsWith(Command.CALORIE_SAMPLE_DISH.getCommandText()));
        // Следом — про профиль, с кнопкой «Заполнить профиль»
        assertThat(text.getAllValues().get(1)).contains("Заполни профиль", "Теперь твоя очередь");
        assertThat(getCallbackData(keyboard.getAllValues().get(1)))
                .containsExactly(Command.CALORIE_PROFILE_DIALOGUE.getCommandText());
    }

    @Test
    void profileFilledNoProfileCall() {
        when(profileService.getOrCreateProfile(APP_USER_ID)).thenReturn(mock(UserNutritionProfile.class, inv ->
                inv.getMethod().getName().equals("checkTargetCalculateParams") ? Boolean.TRUE : null));

        handler.handle(buildCallbackUpdate(Command.CALORIE_SAMPLE_DISH.getCommandText()), buildUser());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture(), isNull(), isNull());
        assertThat(text.getValue()).doesNotContain("Заполни профиль").contains("Теперь твоя очередь");
    }

    @Test
    void weightPlusRedrawsCardInPlace() {
        handler.handle(buildCallbackUpdate("/sampledish 380 1 w10"), buildUser());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InlineKeyboardMarkup> keyboard = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(bot).sendEditMessage(eq(CHAT_ID.longValue()), text.capture(), eq(MESSAGE_ID), keyboard.capture());
        // 380 г × 1.1 = 418 г, 410 ккал × 1.1 = 451
        assertThat(text.getValue()).contains("418", "+451");
        assertThat(getCallbackData(keyboard.getValue())).contains("/sampledish 418 1 w10");
        verify(settingsService, never()).claimFirstDishSample(anyLong(), any());
    }

    @Test
    void deleteAndFavoriteShowHints() {
        handler.handle(buildCallbackUpdate("/sampledish 380 1 del"), buildUser());
        handler.handle(buildCallbackUpdate("/sampledish 380 1 fav"), buildUser());

        verify(bot).answerCallbackQuery(any(), eq(texts.get(AppLanguage.RU, BotTextKeys.START_SAMPLE_HINT_DELETE)));
        verify(bot).answerCallbackQuery(any(), eq(texts.get(AppLanguage.RU, BotTextKeys.START_SAMPLE_HINT_FAVORITE)));
        verify(bot, never()).sendEditMessage(anyLong(), anyString(), anyInt(), any());
    }

    @Test
    void portionsAndLimits() {
        assertThat(SampleDishCalorieUpdateHandler.applyActionOrNull(new SampleState(380, 1), "p2"))
                .isEqualTo(new SampleState(380, 2));
        assertThat(SampleDishCalorieUpdateHandler.applyActionOrNull(new SampleState(380, 1), "p0")).isNull();
        assertThat(SampleDishCalorieUpdateHandler.applyActionOrNull(new SampleState(40, 1), "w-10")).isNull();
        assertThat(SampleDishCalorieUpdateHandler.applyActionOrNull(new SampleState(380, 1), "junk")).isNull();
    }

    @Test
    void twoPortionsDoubleCalories() {
        assertThat(handler.buildCardText(new SampleState(380, 2), AppLanguage.EN))
                .contains("Oatmeal with banana and coffee", "+820 kcal", "1580/2000");
    }

    private static List<String> getCallbackData(ReplyKeyboard keyboard) {
        return ((InlineKeyboardMarkup) keyboard).getKeyboard().stream()
                .flatMap(List::stream)
                .map(InlineKeyboardButton::getCallbackData)
                .toList();
    }

    private static TelegramUser buildUser() {
        return new TelegramUser().setAppUserId(APP_USER_ID);
    }

    private static Update buildCallbackUpdate(String data) {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        Message message = new Message();
        message.setChat(chat);
        message.setMessageId(MESSAGE_ID);
        CallbackQuery query = new CallbackQuery();
        query.setMessage(message);
        query.setData(data);
        Update update = new Update();
        update.setCallbackQuery(query);
        return update;
    }
}
