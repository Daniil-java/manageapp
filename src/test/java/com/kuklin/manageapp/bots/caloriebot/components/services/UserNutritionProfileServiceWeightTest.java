package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UserNutritionProfileRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Журнал веса ↔ вес в профиле: последнее взвешивание = текущий вес, норма пересчитывается.
 */
class UserNutritionProfileServiceWeightTest {

    private static final Long USER_ID = 7L;

    private UserNutritionProfileRepository profileRepository;
    private WeightEntryService weightEntryService;
    private UserNutritionProfileEntryService entryService;
    private UserNutritionProfileService service;
    private UserNutritionProfile profile;

    @BeforeEach
    void setUp() {
        profileRepository = mock(UserNutritionProfileRepository.class);
        weightEntryService = mock(WeightEntryService.class);
        entryService = mock(UserNutritionProfileEntryService.class);
        service = new UserNutritionProfileService(profileRepository, weightEntryService, entryService);

        profile = completeProfile(new BigDecimal("70.0"));
        when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(weightEntryService.updateWeight(any(), any()))
                .thenAnswer(inv -> new WeightEntry().setUserId(inv.getArgument(0)).setWeight(inv.getArgument(1)));
    }

    private static UserNutritionProfile completeProfile(BigDecimal weight) {
        return new UserNutritionProfile()
                .setUserId(USER_ID)
                .setSex(UserNutritionProfile.Sex.FEMALE)
                .setAgeYears(31)
                .setHeightCm(166)
                .setCurrentWeightKg(weight)
                .setActivityLevel(UserNutritionProfile.ActivityLevel.MEDIUM)
                .setGoal(UserNutritionProfile.Goal.LOSE_WEIGHT);
    }

    @Test
    void logWeightUpdatesProfileAndRecalculatesNorm() {
        WeightEntry entry = service.logWeight(USER_ID, new BigDecimal("69.5"));

        assertThat(entry.getWeight()).isEqualByComparingTo("69.5");
        assertThat(profile.getCurrentWeightKg()).isEqualByComparingTo("69.5");
        // (10·69.5 + 6.25·166 − 5·31 − 161) · 1.55 · 0.8 = 1756.46
        assertThat(profile.getCaloriesNormPerDay()).isEqualTo(1756);
        assertThat(profile.getWaterTargetMlPerDay()).isEqualTo(2294);
        verify(weightEntryService).updateWeight(USER_ID, new BigDecimal("69.5"));
        verify(entryService).syncWithProfile(profile);
    }

    @Test
    void logWeightOnIncompleteProfileSavesWeightWithoutNorm() {
        profile.setSex(null).setGoal(null);

        service.logWeight(USER_ID, new BigDecimal("80"));

        assertThat(profile.getCurrentWeightKg()).isEqualByComparingTo("80");
        assertThat(profile.getCaloriesNormPerDay()).isNull();
        verify(profileRepository).save(profile);
        verify(weightEntryService).updateWeight(USER_ID, new BigDecimal("80"));
    }

    @Test
    void invalidWeightIsRejectedBeforeLogging() {
        assertThatThrownBy(() -> service.logWeight(USER_ID, new BigDecimal("600")))
                .isInstanceOf(ErrorResponseException.class);

        verify(weightEntryService, never()).updateWeight(any(), any());
    }

    @Test
    void deletingLatestEntryRollsProfileBackToPreviousWeight() {
        when(weightEntryService.getLatestOrNull(USER_ID))
                .thenReturn(new WeightEntry().setWeight(new BigDecimal("72.00")));

        service.deleteWeightEntry(USER_ID, 5L);

        verify(weightEntryService).deleteWeightEntryById(USER_ID, 5L);
        assertThat(profile.getCurrentWeightKg()).isEqualByComparingTo("72");
        assertThat(profile.getCaloriesNormPerDay()).isNotNull();
        verify(profileRepository).save(profile);
    }

    @Test
    void deletingOlderEntryKeepsProfile() {
        when(weightEntryService.getLatestOrNull(USER_ID))
                .thenReturn(new WeightEntry().setWeight(new BigDecimal("70.00")));

        service.deleteWeightEntry(USER_ID, 5L);

        verify(profileRepository, never()).save(any());
    }

    @Test
    void deletingLastEntryOfJournalKeepsProfileWeight() {
        when(weightEntryService.getLatestOrNull(USER_ID)).thenReturn(null);

        service.deleteWeightEntry(USER_ID, 5L);

        assertThat(profile.getCurrentWeightKg()).isEqualByComparingTo("70");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void newWeightInProfileGoesToJournal() {
        service.patchProfileDto(USER_ID, new UserNutritionProfileDto().setCurrentWeightKg(new BigDecimal("68")));

        verify(weightEntryService).updateWeight(USER_ID, new BigDecimal("68"));
    }

    @Test
    void sameOrMissingWeightInProfileIsNotLogged() {
        service.patchProfileDto(USER_ID, new UserNutritionProfileDto().setCurrentWeightKg(new BigDecimal("70.00")));
        service.patchProfileDto(USER_ID, new UserNutritionProfileDto().setAgeYears(32));

        verify(weightEntryService, never()).updateWeight(any(), any());
    }
}
