package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UserNutritionProfileRepository;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.NormMode;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserNutritionProfileDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Норма калорий: AUTO — по формуле, MANUAL — своя, БЖУ от неё.
 */
class UserNutritionProfileServiceNormModeTest {

    private static final Long USER_ID = 7L;

    private UserNutritionProfileService service;
    private UserNutritionProfile profile;

    @BeforeEach
    void setUp() {
        UserNutritionProfileRepository profileRepository = mock(UserNutritionProfileRepository.class);
        service = new UserNutritionProfileService(
                profileRepository, mock(WeightEntryService.class), mock(UserNutritionProfileEntryService.class));

        // Ж, 31 год, 166 см, 69.5 кг, средняя активность, похудение → 1756 ккал по формуле
        profile = new UserNutritionProfile()
                .setUserId(USER_ID)
                .setSex(UserNutritionProfile.Sex.FEMALE)
                .setAgeYears(31)
                .setHeightCm(166)
                .setCurrentWeightKg(new BigDecimal("69.5"))
                .setActivityLevel(UserNutritionProfile.ActivityLevel.MEDIUM)
                .setGoal(UserNutritionProfile.Goal.LOSE_WEIGHT);
        when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private UserNutritionProfileDto put(UserNutritionProfileDto dto) {
        return service.patchProfileDto(USER_ID, dto);
    }

    @Test
    void autoModeIgnoresSentCaloriesAndUsesFormula() {
        UserNutritionProfileDto saved = put(new UserNutritionProfileDto().setCaloriesNormPerDay(2500));

        assertThat(saved.getNormMode()).isEqualTo(NormMode.AUTO);
        assertThat(saved.getCaloriesNormPerDay()).isEqualTo(1756);
        assertThat(saved.getProteinsNormGramsPerDay()).isEqualTo(139);
        assertThat(saved.getWaterTargetMlPerDay()).isEqualTo(2294);
    }

    @Test
    void manualModeKeepsCaloriesAndDerivesMacros() {
        UserNutritionProfileDto saved = put(new UserNutritionProfileDto()
                .setNormMode(NormMode.MANUAL)
                .setCaloriesNormPerDay(2000));

        assertThat(saved.getNormMode()).isEqualTo(NormMode.MANUAL);
        assertThat(saved.getCaloriesNormPerDay()).isEqualTo(2000);
        // белок от веса (69.5 × 2), жиры 25% ккал, углеводы — остаток
        assertThat(saved.getProteinsNormGramsPerDay()).isEqualTo(139);
        assertThat(saved.getFatsNormGramsPerDay()).isEqualTo(55);
        assertThat(saved.getCarbsNormGramsPerDay()).isEqualTo((2000 - 139 * 4 - 55 * 9) / 4);
        assertThat(saved.getWaterTargetMlPerDay()).isEqualTo(2294);
    }

    @Test
    void manualCaloriesSurviveProfileChanges() {
        put(new UserNutritionProfileDto().setNormMode(NormMode.MANUAL).setCaloriesNormPerDay(2000));

        UserNutritionProfileDto saved = put(new UserNutritionProfileDto()
                .setCurrentWeightKg(new BigDecimal("80"))
                .setGoal(UserNutritionProfile.Goal.GAIN_WEIGHT));

        assertThat(saved.getCaloriesNormPerDay()).isEqualTo(2000);
        assertThat(saved.getProteinsNormGramsPerDay()).isEqualTo(144); // 80 × 1.8
        assertThat(saved.getWaterTargetMlPerDay()).isEqualTo(2640);    // 80 × 33
    }

    @Test
    void switchingModeOnWithoutCaloriesKeepsCurrentNumber() {
        put(new UserNutritionProfileDto()); // AUTO: 1756

        UserNutritionProfileDto saved = put(new UserNutritionProfileDto().setNormMode(NormMode.MANUAL));

        assertThat(saved.getNormMode()).isEqualTo(NormMode.MANUAL);
        assertThat(saved.getCaloriesNormPerDay()).isEqualTo(1756);
    }

    @Test
    void backToAutoRecalculatesByFormula() throws Exception {
        put(new UserNutritionProfileDto().setNormMode(NormMode.MANUAL).setCaloriesNormPerDay(2500));

        UserNutritionProfile saved = service.switchToAutoNorm(profile);

        assertThat(saved.getNormMode()).isEqualTo(NormMode.AUTO);
        assertThat(saved.getCaloriesNormPerDay()).isEqualTo(1756);
    }

    @Test
    void negativeManualCaloriesAreRejected() {
        assertThatThrownBy(() -> put(new UserNutritionProfileDto()
                .setNormMode(NormMode.MANUAL)
                .setCaloriesNormPerDay(-1)))
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void anyNonNegativeManualCaloriesAreAccepted() {
        UserNutritionProfileDto zero = put(new UserNutritionProfileDto()
                .setNormMode(NormMode.MANUAL)
                .setCaloriesNormPerDay(0));

        assertThat(zero.getCaloriesNormPerDay()).isZero();
        // Белок от веса больше калорий — углеводы не уходят в минус
        assertThat(zero.getFatsNormGramsPerDay()).isZero();
        assertThat(zero.getCarbsNormGramsPerDay()).isZero();

        UserNutritionProfileDto huge = put(new UserNutritionProfileDto()
                .setNormMode(NormMode.MANUAL)
                .setCaloriesNormPerDay(Integer.MAX_VALUE));

        assertThat(huge.getCaloriesNormPerDay()).isEqualTo(Integer.MAX_VALUE);
        assertThat(huge.getCarbsNormGramsPerDay()).isPositive();
    }

    @Test
    void manualNormWorksOnEmptyProfile() {
        profile.setSex(null).setAgeYears(null).setHeightCm(null)
                .setCurrentWeightKg(null).setActivityLevel(null).setGoal(null)
                .setWaterTargetMlPerDay(2000);

        UserNutritionProfileDto saved = put(new UserNutritionProfileDto()
                .setNormMode(NormMode.MANUAL)
                .setCaloriesNormPerDay(2000));

        // Без веса: белки 30%, жиры 25%, углеводы — остаток; вода не меняется
        assertThat(saved.getProteinsNormGramsPerDay()).isEqualTo(150);
        assertThat(saved.getFatsNormGramsPerDay()).isEqualTo(55);
        assertThat(saved.getCarbsNormGramsPerDay()).isEqualTo((2000 - 150 * 4 - 55 * 9) / 4);
        assertThat(saved.getWaterTargetMlPerDay()).isEqualTo(2000);
        assertThat(service.checkTargetCalculateParams(profile)).isTrue();
    }

    // ===== Вода: свой режим, независимый от калорий =====

    @Test
    void autoWaterIgnoresSentValueAndUsesFormula() {
        UserNutritionProfileDto saved = put(new UserNutritionProfileDto().setWaterTargetMlPerDay(1500));

        assertThat(saved.getWaterMode()).isEqualTo(NormMode.AUTO);
        assertThat(saved.getWaterTargetMlPerDay()).isEqualTo(2294); // 69.5 × 33
    }

    @Test
    void manualWaterSurvivesProfileChanges() {
        put(new UserNutritionProfileDto().setWaterMode(NormMode.MANUAL).setWaterTargetMlPerDay(1500));

        UserNutritionProfileDto saved = put(new UserNutritionProfileDto()
                .setCurrentWeightKg(new BigDecimal("80"))
                .setActivityLevel(UserNutritionProfile.ActivityLevel.HIGH));

        assertThat(saved.getWaterMode()).isEqualTo(NormMode.MANUAL);
        assertThat(saved.getWaterTargetMlPerDay()).isEqualTo(1500);
        // калории при этом в AUTO — пересчитались
        assertThat(saved.getNormMode()).isEqualTo(NormMode.AUTO);
        assertThat(saved.getCaloriesNormPerDay()).isNotEqualTo(1756);
    }

    @Test
    void waterModeIsIndependentFromCaloriesMode() {
        put(new UserNutritionProfileDto().setNormMode(NormMode.MANUAL).setCaloriesNormPerDay(2000));

        UserNutritionProfileDto saved = put(new UserNutritionProfileDto().setCurrentWeightKg(new BigDecimal("80")));

        assertThat(saved.getCaloriesNormPerDay()).isEqualTo(2000);
        assertThat(saved.getWaterMode()).isEqualTo(NormMode.AUTO);
        assertThat(saved.getWaterTargetMlPerDay()).isEqualTo(2640); // 80 × 33
    }

    @Test
    void switchToAutoWaterUsesFormulaEvenWithoutCaloriesData() throws Exception {
        profile.setSex(null).setAgeYears(null).setHeightCm(null).setGoal(null)
                .setWaterMode(NormMode.MANUAL).setWaterTargetMlPerDay(1500);

        UserNutritionProfile saved = service.switchToAutoWater(profile);

        assertThat(saved.getWaterMode()).isEqualTo(NormMode.AUTO);
        assertThat(saved.getWaterTargetMlPerDay()).isEqualTo(2294);
    }

    @Test
    void switchToAutoWaterNeedsWeightAndActivity() {
        profile.setCurrentWeightKg(null).setWaterMode(NormMode.MANUAL).setWaterTargetMlPerDay(1500);

        assertThatThrownBy(() -> service.switchToAutoWater(profile))
                .isInstanceOf(InsufficientProfileDataException.class);
        assertThat(profile.getWaterTargetMlPerDay()).isEqualTo(1500);
    }

    @Test
    void negativeManualWaterIsRejected() {
        assertThatThrownBy(() -> put(new UserNutritionProfileDto()
                .setWaterMode(NormMode.MANUAL)
                .setWaterTargetMlPerDay(-1)))
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void formulaHintIsAvailableOnlyForFilledProfile() {
        assertThat(service.calcFormulaCaloriesOrNull(profile)).isEqualTo(1756);

        profile.setGoal(null);
        assertThat(service.calcFormulaCaloriesOrNull(profile)).isNull();
    }
}
