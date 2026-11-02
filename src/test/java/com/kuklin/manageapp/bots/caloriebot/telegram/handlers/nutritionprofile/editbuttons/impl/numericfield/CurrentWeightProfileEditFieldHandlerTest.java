package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Вес из профиля попадает в журнал только после валидации профиля — как в logWeight.
 */
class CurrentWeightProfileEditFieldHandlerTest {

    private UserNutritionProfileService profileService;
    private CurrentWeightProfileEditFieldHandler handler;

    @BeforeEach
    void setUp() {
        profileService = mock(UserNutritionProfileService.class);
        handler = new CurrentWeightProfileEditFieldHandler(profileService, null);
    }

    @Test
    void invalidWeightIsNotWrittenToJournal() throws Exception {
        UserNutritionProfile profile = handler.applyValue(new UserNutritionProfile().setUserId(1L), new BigDecimal("999.0"));
        when(profileService.patchProfile(any())).thenThrow(mock(UserNutritionProfileValidationException.class));

        assertThatThrownBy(() -> handler.patchProfile(profile)).isInstanceOf(UserNutritionProfileValidationException.class);

        verify(profileService, never()).updateCurrentWeight(anyLong(), any());
    }

    @Test
    void validWeightIsWrittenToJournalAfterProfile() throws Exception {
        BigDecimal weight = new BigDecimal("72.5");
        UserNutritionProfile profile = handler.applyValue(new UserNutritionProfile().setUserId(1L), weight);
        when(profileService.patchProfile(profile)).thenReturn(profile);

        handler.patchProfile(profile);

        var order = inOrder(profileService);
        order.verify(profileService).patchProfile(profile);
        order.verify(profileService).updateCurrentWeight(1L, weight);
    }
}
