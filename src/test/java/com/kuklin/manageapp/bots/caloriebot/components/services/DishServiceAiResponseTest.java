package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.components.repository.DishRepository;
import com.kuklin.manageapp.bots.caloriebot.configurations.DishLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Разбор ответа ИИ: «нет еды / кривой ответ» → null, сбой сохранения → DISH_SAVE_FAILED,
 * чтобы FeatureAccessAspect вернул попытку, а не засчитал её как «на фото нет еды».
 */
@SuppressWarnings("unchecked")
class DishServiceAiResponseTest {

    private static final Long USER_ID = 7L;
    private static final String DISH_JSON =
            "[{\"name\":\"Омлет\",\"calories\":250,\"weight\":150,\"portions\":1,\"isDish\":true}]";

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

        service = new DishService(mock(DishRepository.class), openAi,
                mock(TelegramCaloriesBotKeyComponents.class), new ObjectMapper(),
                mock(UserSettingsService.class), mock(CalorieAccessService.class),
                mock(TelegramUserService.class), selfProvider, mock(AiInputValidator.class),
                mock(AiRateLimiter.class), mock(DishLimitsProperties.class));
    }

    private void aiAnswers(String json) {
        when(openAi.fetchResponse(any(), anyString(), any(), anyString(), any())).thenReturn(json);
    }

    @Test
    void savedDishesAreReturned() {
        aiAnswers(DISH_JSON);
        when(self.saveDishes(eq(USER_ID), anyList())).thenAnswer(inv -> inv.getArgument(1));

        List<Dish> dishes = service.getDishByDescriptionOrNull(USER_ID, "омлет");

        assertThat(dishes).hasSize(1);
        assertThat(dishes.get(0).getName()).isEqualTo("Омлет");
        assertThat(dishes.get(0).getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void saveFailureIsNotReportedAsNoFood() {
        aiAnswers(DISH_JSON);
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("DB is down");
        when(self.saveDishes(eq(USER_ID), anyList())).thenThrow(dbDown);

        assertThatThrownBy(() -> service.getDishByDescriptionOrNull(USER_ID, "омлет"))
                .isInstanceOf(ErrorResponseException.class)
                .hasCause(dbDown)
                .extracting(e -> ((ErrorResponseException) e).getErrorStatus())
                .isEqualTo(ErrorStatus.DISH_SAVE_FAILED);
    }

    @Test
    void brokenJsonIsNoFood() {
        aiAnswers("sorry, I can't help with that");

        assertThat(service.getDishByDescriptionOrNull(USER_ID, "омлет")).isNull();
        verify(self, never()).saveDishes(anyLong(), anyList());
    }

    @Test
    void notADishIsNoFoodAndNothingIsSaved() {
        aiAnswers("[{\"name\":\"Стол\",\"isDish\":false}]");

        assertThat(service.getDishByDescriptionOrNull(USER_ID, "стол")).isNull();
        verify(self, never()).saveDishes(anyLong(), anyList());
    }
}
