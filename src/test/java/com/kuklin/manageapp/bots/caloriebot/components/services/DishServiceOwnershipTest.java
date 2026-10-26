package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.components.repository.DishRepository;
import com.kuklin.manageapp.bots.caloriebot.configurations.DishLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Блюдо меняет и удаляет только его владелец: id блюда приходит из пути запроса или callback_data бота,
 * и подделать его может кто угодно.
 */
@SuppressWarnings("unchecked")
class DishServiceOwnershipTest {

    private static final Long OWNER = 7L;
    private static final Long STRANGER = 8L;
    private static final Long DISH_ID = 100L;

    private DishService service;
    private DishRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(DishRepository.class);
        service = new DishService(repository, mock(OpenAiProviderProcessor.class),
                mock(TelegramCaloriesBotKeyComponents.class), new ObjectMapper(),
                mock(UserSettingsService.class), mock(CalorieAccessService.class),
                mock(TelegramUserService.class), mock(ObjectProvider.class), mock(AiInputValidator.class),
                mock(AiRateLimiter.class), mock(DishLimitsProperties.class), mock(CalorieTexts.class),
                mock(ApplicationEventPublisher.class));

        Dish dish = new Dish().setId(DISH_ID).setUserId(OWNER).setName("Омлет")
                .setCalories(250).setProteins(15).setFats(18).setCarbohydrates(2)
                .setWeight(150).setPortions(1).setPortionWeight(150);
        when(repository.findById(DISH_ID)).thenReturn(Optional.of(dish));
        when(repository.findByIdAndUserId(DISH_ID, OWNER)).thenReturn(Optional.of(dish));
        when(repository.findByIdAndUserId(DISH_ID, STRANGER)).thenReturn(Optional.empty());
        when(repository.save(any(Dish.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void portionUpdateIgnoresIdFromBody() {
        // В теле — id другого блюда: раньше save() обновил бы его и присвоил этому пользователю
        DishDto request = new DishDto().setId(999L).setName("Омлет").setPortions(2);

        service.updateDishPortion(OWNER, DISH_ID, request);

        ArgumentCaptor<Dish> saved = ArgumentCaptor.forClass(Dish.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(DISH_ID);
        assertThat(saved.getValue().getPortions()).isEqualTo(2);
    }

    @Test
    void portionUpdateOfStrangersDishRejected() {
        assertThatThrownBy(() -> service.updateDishPortion(STRANGER, DISH_ID, new DishDto().setName("x")))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.DISH_NOT_BELONG_USER));
        verify(repository, never()).save(any());
    }

    @Test
    void portionUpdateOfMissingDishIsNotFound() {
        assertThatThrownBy(() -> service.updateDishPortion(OWNER, 555L, new DishDto().setName("x")))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.DISH_NOT_FOUND));
    }

    @Test
    void strangerCannotDeleteDish() {
        assertThat(service.removeUserDish(STRANGER, DISH_ID)).isFalse();
        verify(repository, never()).delete(any());
    }

    @Test
    void ownerDeletesDish() {
        assertThat(service.removeUserDish(OWNER, DISH_ID)).isTrue();
        verify(repository).delete(any(Dish.class));
    }

    @Test
    void strangerCannotScaleDish() {
        assertThat(service.changePortionWeightByPercentOrNull(STRANGER, DISH_ID, 10)).isNull();
        assertThat(service.saveNewPortionsCountOrNull(STRANGER, DISH_ID, 2)).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void zeroOrNegativePortionsRejected() {
        assertThat(service.saveNewPortionsCountOrNull(OWNER, DISH_ID, 0)).isNull();
        assertThat(service.saveNewPortionsCountOrNull(OWNER, DISH_ID, -3)).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void hugeValuesDoNotOverflow() {
        // Своё блюдо — любые значения можно, но вес не уходит в минус из-за переполнения int
        assertThat(service.saveNewPortionsCountOrNull(OWNER, DISH_ID, 100_000_000).getWeight())
                .isEqualTo(Integer.MAX_VALUE);
        assertThat(service.changePortionWeightByPercentOrNull(OWNER, DISH_ID, -1000).getPortionWeight()).isZero();
    }

    @Test
    void ownerScalesDish() {
        assertThat(service.changePortionWeightByPercentOrNull(OWNER, DISH_ID, 10).getPortionWeight()).isEqualTo(165);
    }
}
