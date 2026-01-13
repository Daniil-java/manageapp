package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.models.UserNutritionDto;
import com.kuklin.manageapp.bots.caloriebot.repository.UserNutritionProfileRepository;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation.InvalidAgeException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation.InvalidHeightException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation.InvalidWeightException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation.UserNutritionProfileValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.*;

/*
* Сервис профиля питания пользователя
*
*
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserNutritionProfileService {
    private final UserNutritionProfileRepository userNutritionProfileRepository;
    private final WeightEntryService weightEntryService;
    private final UserNutritionProfileEntryService userNutritionProfileEntryService;

    /**
     * Получить профиль или создать пустой (только userId).
     */
    @Transactional
    public UserNutritionProfile getOrCreateProfile(Long userId) {
        return userNutritionProfileRepository.findByUserId(userId)
                .orElseGet(() -> userNutritionProfileRepository.save(
                        new UserNutritionProfile()
                                .setUserId(userId)
                                .setWaterTargetMlPerDay(DEF_WATER_ML)
                ));
    }

    /**
     * Обновить только текущий вес из /weight 78.5.
     * Можно дергать после записи WeightEntry.
     */
    @Transactional
    public void updateCurrentWeight(Long userId, BigDecimal currentWeightKg) {
        weightEntryService.updateWeight(userId, currentWeightKg);
    }

    /**
     * Обновить только цель по воде (когда пользователь меняет её явно).
     */
    @Transactional
    public UserNutritionProfile updateWaterTarget(Long userId, Integer waterTargetMlPerDay) {
        UserNutritionProfile profile = getOrCreateProfile(userId)
                .setWaterTargetMlPerDay(waterTargetMlPerDay);

        // Ничего пересчитывать не надо, только обновляем поле
        profile = userNutritionProfileRepository.save(profile);
        userNutritionProfileEntryService.syncWithProfile(profile);
        return profile;
    }

    //Валидация данных, без обращений в репозиторий
    private static void validateProfile(UserNutritionProfile profile)
            throws UserNutritionProfileValidationException {

        Integer age = profile.getAgeYears();
        Integer height = profile.getHeightCm();
        BigDecimal weight = profile.getCurrentWeightKg();

        if (age != null && (age < AGE_MIN || age > AGE_MAX)) {
            throw new InvalidAgeException(age);
        }

        if (height != null && (height < HEIGHT_MIN || height > HEIGHT_MAX)) {
            throw new InvalidHeightException(height);
        }

        if (weight != null &&
                (weight.compareTo(BigDecimal.valueOf(WEIGHT_MIN)) < 0
                        || weight.compareTo(BigDecimal.valueOf(WEIGHT_MAX)) > 0)) {
            throw new InvalidWeightException(weight);
        }
    }

    /**
     * Валидировать профиль (диапазоны возраста/роста/веса).
     * Можно дергать перед сохранением анкеты.
     */
    @Transactional
    public UserNutritionProfile validateAndSave(UserNutritionProfile profile)
            throws UserNutritionProfileValidationException {

        validateProfile(profile);
        return userNutritionProfileRepository.save(profile);
    }

    //Пересчет пользовательских целей и сохранение
    @Transactional
    public UserNutritionProfile recalculateAndSave(UserNutritionProfile profile) throws InsufficientProfileDataException, UserNutritionProfileValidationException {
        UserNutritionDto dto = recalcTargets(profile);
        profile = UserNutritionDto.updateNutritionData(profile, dto);
        profile = validateAndSave(profile);
        userNutritionProfileEntryService.syncWithProfile(profile);
        return profile;
    }

    /**
     * Пересчёт нормы калорий и БЖУ.
     */
    private UserNutritionDto recalcTargets(UserNutritionProfile profile)
            throws InsufficientProfileDataException {

        checkTargetCalculateParamsOrThrow(profile);

        double weight = profile.getCurrentWeightKg().doubleValue();

        // --- BMR (Mifflin–St Jeor) ---
        double bmrCalories =
                10 * weight
                        + 6.25 * profile.getHeightCm()
                        - 5 * profile.getAgeYears();

        if (profile.getSex() == UserNutritionProfile.Sex.MALE) {
            bmrCalories += 5;
        } else {
            bmrCalories -= 161;
        }

        // --- Activity & goal (дефицит / профицит) ---
        bmrCalories *= profile.getActivityLevel().getCoef();
        bmrCalories *= profile.getGoal().getCoef();

        int caloriesTarget = (int) Math.round(bmrCalories);

        // --- Proteins (от текущего веса — нормально даже при похудении) ---
        int proteins = (int) Math.round(
                weight * profile.getGoal().getProteinsPerKg()
        );

        // --- Fats (процент от калорий, а не от веса) ---
        // 20–30% — норма, берём 25%
        int fatsCalories = (int) Math.round(caloriesTarget * 0.25);
        int fats = fatsCalories / 9;

        // --- Carbs (остаток) ---
        int carbs = (caloriesTarget - proteins * 4 - fats * 9) / 4;

        // --- Water ---
        int waterTarget = (int) Math.round(
                weight * profile.getActivityLevel().getWaterMlPerKg()
        );

        return new UserNutritionDto()
                .setCaloriesNormPerDay(caloriesTarget)
                .setProteinsNormGramsPerDay(proteins)
                .setFatsNormGramsPerDay(fats)
                .setCarbsNormGramsPerDay(Math.max(carbs, 0))
                .setWaterTargetMlPerDay(waterTarget);
    }

    //Проверка достаточности существующих данных
    public boolean checkTargetCalculateParams(UserNutritionProfile profile) {
        try {
            checkTargetCalculateParamsOrThrow(profile);
            return true;
        } catch (InsufficientProfileDataException e) {
            return false;
        }
    }
    //Проверка достаточности существующих данных или выброс ошибки
    private void checkTargetCalculateParamsOrThrow(UserNutritionProfile profile)
            throws InsufficientProfileDataException {

        if (profile == null)
            throw new InsufficientProfileDataException("profile");

        if (profile.getSex() == null)
            throw new InsufficientProfileDataException("Пол");

        if (profile.getAgeYears() == null)
            throw new InsufficientProfileDataException("Возраст");

        if (profile.getHeightCm() == null)
            throw new InsufficientProfileDataException("Рост");

        if (profile.getCurrentWeightKg() == null)
            throw new InsufficientProfileDataException("Вес");

        if (profile.getActivityLevel() == null)
            throw new InsufficientProfileDataException("Активность");

        if (profile.getGoal() == null)
            throw new InsufficientProfileDataException("Цель");
    }

    /**
     * PATCH-подобное обновление профиля.
     * null-поля игнорируются.
     * Метод либо возвращает валидный профиль,
     * либо кидает UserNutritionProfileException.
     */
    @Transactional
    public UserNutritionProfile patchProfile(
            Long userId,
            UserNutritionProfile.Sex sex,
            Integer ageYears,
            Integer heightCm,
            BigDecimal currentWeightKg,
            UserNutritionProfile.ActivityLevel activityLevel,
            UserNutritionProfile.Goal goal,
            Integer waterTargetMlPerDay,
            DietType dietType
    ) throws UserNutritionProfileValidationException{

        UserNutritionProfile profile = getOrCreateProfile(userId);

        if (sex != null) profile.setSex(sex);
        if (ageYears != null) profile.setAgeYears(ageYears);
        if (heightCm != null) profile.setHeightCm(heightCm);
        if (currentWeightKg != null) profile.setCurrentWeightKg(currentWeightKg);
        if (activityLevel != null) profile.setActivityLevel(activityLevel);
        if (goal != null) profile.setGoal(goal);
        if (waterTargetMlPerDay != null) profile.setWaterTargetMlPerDay(waterTargetMlPerDay);
        if (dietType != null) profile.setDietType(dietType);

        profile = validateAndSave(profile);
        userNutritionProfileEntryService.syncWithProfile(profile);
        return profile;
    }
}
