package com.kuklin.manageapp.bots.caloriebot.dish;

import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.dish.ai.AiDishResponse;
import com.kuklin.manageapp.bots.caloriebot.dish.ai.AiInputValidator;
import com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts;
import com.kuklin.manageapp.bots.caloriebot.access.CalorieAccessService;
import com.kuklin.manageapp.bots.caloriebot.access.AiRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.bot.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.web.error.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.web.error.ErrorStatus;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Разбор ответа ИИ: «нет еды / кривой ответ» → null, сбой сохранения → DISH_SAVE_FAILED,
 * чтобы FeatureAccessAspect вернул попытку, а не засчитал её как «на фото нет еды».
 * Пищевая ценность приходит на 100 г — итог на порцию считается по весу.
 * Инструкции неизменны (кэшируются), текст пользователя и его язык — только в сообщении пользователя.
 */
@SuppressWarnings("unchecked")
class DishServiceAiResponseTest {

    private static final Long USER_ID = 7L;
    private static final String DISH_JSON = """
            {"dishes":[{"estimation":"2 яйца и молоко, ~150 г","name":"Омлет","emojiIcon":"🍳",
            "category":"BREAKFAST_ITEM","weightGrams":150,"portions":1,"caloriesPer100g":160,
            "proteinsPer100g":11,"fatsPer100g":12.4,"carbohydratesPer100g":1.6,"aiConfidence":80}]}""";

    private DishService service;
    private OpenAiProviderProcessor openAi;
    // Сам себе через прокси: saveDishes зовётся через selfProvider
    private DishService self;

    @BeforeEach
    void setUp() {
        openAi = mock(OpenAiProviderProcessor.class);
        self = mock(DishService.class);
        ObjectProvider<DishService> selfProvider = mock(ObjectProvider.class);
        when(selfProvider.getObject()).thenReturn(self);
        CalorieTexts texts = mock(CalorieTexts.class);
        when(texts.languageOf(USER_ID)).thenReturn(AppLanguage.EN);

        service = new DishService(mock(DishRepository.class), openAi,
                mock(TelegramCaloriesBotKeyComponents.class), new ObjectMapper(),
                mock(UserSettingsService.class), mock(CalorieAccessService.class),
                mock(TelegramUserService.class), selfProvider, mock(AiInputValidator.class),
                mock(AiRateLimiter.class), mock(DishLimitsProperties.class), texts,
                mock(ApplicationEventPublisher.class));
    }

    private void aiAnswers(String json) {
        when(openAi.fetchStructuredResponse(any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(json);
    }

    @Test
    void savedDishesAreReturnedWithTotalsByWeight() {
        aiAnswers(DISH_JSON);
        when(self.saveDishes(eq(USER_ID), anyList())).thenAnswer(inv -> inv.getArgument(1));

        List<Dish> dishes = service.getDishByDescriptionOrNull(USER_ID, "омлет");

        assertThat(dishes).hasSize(1);
        Dish dish = dishes.get(0);
        assertThat(dish.getName()).isEqualTo("Омлет");
        assertThat(dish.getUserId()).isEqualTo(USER_ID);
        assertThat(dish.getCategory()).isEqualTo(Dish.FoodCategory.BREAKFAST_ITEM);
        assertThat(dish.getWeight()).isEqualTo(150);
        assertThat(dish.getPortionWeight()).isEqualTo(150);
        // 160 × 1.5 = 240; 12.4 × 1.5 = 18.6 → 19
        assertThat(dish.getCalories()).isEqualTo(240);
        assertThat(dish.getProteins()).isEqualTo(17);
        assertThat(dish.getFats()).isEqualTo(19);
        assertThat(dish.getCarbohydrates()).isEqualTo(2);
        assertThat(dish.getAiConfidence()).isEqualTo(80);
    }

    @Test
    void saveFailureIsNotReportedAsNoFood() {
        aiAnswers(DISH_JSON);
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("DB is down");
        when(self.saveDishes(eq(USER_ID), anyList())).thenThrow(dbDown);

        assertThatThrownBy(() -> service.getDishByDescriptionOrNull(USER_ID, "омлет"))
                .isInstanceOf(ErrorResponseException.class)
                .hasCause(dbDown)
                .extracting(e -> ((ErrorResponseException) e).getErrorCode())
                .isEqualTo(ErrorStatus.DISH_SAVE_FAILED);
    }

    @Test
    void brokenJsonIsNoFood() {
        aiAnswers("sorry, I can't help with that");

        assertThat(service.getDishByDescriptionOrNull(USER_ID, "омлет")).isNull();
        verify(self, never()).saveDishes(anyLong(), anyList());
    }

    @Test
    void emptyDishesIsNoFoodAndNothingIsSaved() {
        aiAnswers("{\"dishes\":[]}");

        assertThat(service.getDishByDescriptionOrNull(USER_ID, "стол")).isNull();
        verify(self, never()).saveDishes(anyLong(), anyList());
    }

    @Test
    void dishWithoutNameIsSkipped() {
        aiAnswers("{\"dishes\":[{\"name\":\" \",\"weightGrams\":100,\"caloriesPer100g\":50}]}");

        assertThat(service.getDishByDescriptionOrNull(USER_ID, "что-то")).isNull();
        verify(self, never()).saveDishes(anyLong(), anyList());
    }

    // ── Промпт: инструкции отдельно, данные пользователя отдельно ─────────

    @Test
    void descriptionGoesToUserMessageWithUserLanguage() {
        aiAnswers("{\"dishes\":[]}");

        service.getDishByDescriptionOrNull(USER_ID, "chicken breast 200g");

        ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(openAi).fetchStructuredResponse(any(), eq(DishCalorieBotPrompts.TEXT_INSTRUCTIONS),
                userMessage.capture(), isNull(), eq(AiDishResponse.RESPONSE_FORMAT), any(), any());
        assertThat(userMessage.getValue())
                .contains("Описание: chicken breast 200g")
                .contains("если язык не понять — English");
    }

    @Test
    void photoCaptionGoesToUserMessageWithUserLanguage() {
        aiAnswers("{\"dishes\":[]}");

        service.getDishDtoByPhoto(USER_ID, "data:image/jpeg;base64,AAAA", "my lunch");

        ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(openAi).fetchStructuredResponse(any(), eq(DishCalorieBotPrompts.PHOTO_INSTRUCTIONS),
                userMessage.capture(), eq("data:image/jpeg;base64,AAAA"), eq(AiDishResponse.RESPONSE_FORMAT),
                any(), any());
        assertThat(userMessage.getValue())
                .contains("Подпись к фото: «my lunch»")
                .contains("если язык подписи не понять — на языке English")
                .doesNotContain("подписи нет");
    }

    /** Без подписи — прямое указание языка (раньше в промпт попадало «null», и названия выходили русскими). */
    @Test
    void photoWithoutCaptionAsksForUserLanguageOnly() {
        aiAnswers("{\"dishes\":[]}");

        service.getDishDtoByPhoto(USER_ID, "data:image/jpeg;base64,AAAA", null);

        ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(openAi).fetchStructuredResponse(any(), anyString(), userMessage.capture(), anyString(),
                any(), any(), any());
        assertThat(userMessage.getValue())
                .contains("Подпись к фото: нет")
                .contains("подписи нет, поэтому название — ТОЛЬКО на языке English")
                .doesNotContain("null")
                .doesNotContain("на языке подписи");
    }

    /** Инструкции без подстановок — иначе OpenAI не закэширует их как общее начало запроса. */
    @Test
    void instructionsHaveNoPlaceholders() {
        assertThat(DishCalorieBotPrompts.PHOTO_INSTRUCTIONS).doesNotContain("%");
        assertThat(DishCalorieBotPrompts.TEXT_INSTRUCTIONS).doesNotContain("%");
    }
}
