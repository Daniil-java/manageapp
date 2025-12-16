package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.models.UserNutritionDto;
import com.kuklin.manageapp.bots.caloriebot.repository.UserNutritionProfileRepository;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.UserNutritionProfileException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation.InvalidAgeException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation.InvalidHeightException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation.InvalidWeightException;
import com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation.UserNutritionProfileValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
public class UserNutritionProfileService {
    private final UserNutritionProfileRepository userNutritionProfileRepository;

    /**
     * Получить профиль или создать пустой (только userId).
     */
    public UserNutritionProfile getOrCreateProfile(Long userId) {
        return userNutritionProfileRepository.findByUserId(userId)
                .orElseGet(() -> userNutritionProfileRepository.save(
                        new UserNutritionProfile()
                                .setUserId(userId)
                ));
    }

    /**
     * Обновить только текущий вес из /weight 78.5.
     * Можно дергать после записи WeightEntry.
     */
    public UserNutritionProfile updateCurrentWeight(Long userId, BigDecimal currentWeightKg) throws UserNutritionProfileException {
        UserNutritionProfile profile = getOrCreateProfile(userId)
                .setCurrentWeightKg(currentWeightKg);

        return  validateAndSave(profile);
    }

    /**
     * Обновить только цель по воде (когда пользователь меняет её явно).
     */
    public UserNutritionProfile updateWaterTarget(Long userId, Integer waterTargetMlPerDay) {
        UserNutritionProfile profile = getOrCreateProfile(userId)
                .setWaterTargetMlPerDay(waterTargetMlPerDay);

        // Ничего пересчитывать не надо, только обновляем поле
        return userNutritionProfileRepository.save(profile);
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
    public UserNutritionProfile validateAndSave(UserNutritionProfile profile)
            throws UserNutritionProfileValidationException {

        validateProfile(profile);
        return userNutritionProfileRepository.save(profile);
    }

    //Пересчет пользовательских целей и сохранение
    public UserNutritionProfile recalculateAndSave(UserNutritionProfile profile) throws InsufficientProfileDataException, UserNutritionProfileValidationException {
        UserNutritionDto dto = recalcTargets(profile);
        profile = UserNutritionDto.updateNutritionData(profile, dto);
        return validateAndSave(profile);
    }

    /**
     * Пересчёт нормы калорий и БЖУ.
     */
    private UserNutritionDto recalcTargets(UserNutritionProfile profile)
            throws InsufficientProfileDataException {

        checkTargetCalculateParamsOrThrow(profile);

        double bmrCalories = 10 * profile.getCurrentWeightKg().doubleValue()
                + 6.25 * profile.getHeightCm()
                - 5 * profile.getAgeYears();

        if (profile.getSex() == UserNutritionProfile.Sex.MALE) bmrCalories += 5;
        else bmrCalories -= 161;

        bmrCalories *= profile.getActivityLevel().getCoef();
        bmrCalories *= profile.getGoal().getCoef();

        int proteins = (int) (profile.getCurrentWeightKg().doubleValue()
                * profile.getGoal().getProteinsPerKg());
        int fats = (int) (profile.getCurrentWeightKg().doubleValue()
                * profile.getGoal().getFatsPerKg());
        int carbs = (int) ((bmrCalories - proteins * 4 - fats * 9) / 4);

        return new UserNutritionDto()
                .setCaloriesNormPerDay((int) bmrCalories)
                .setProteinsNormGramsPerDay(proteins)
                .setFatsNormGramsPerDay(fats)
                .setCarbsNormGramsPerDay(carbs);
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
    public UserNutritionProfile patchProfile(
            Long userId,
            UserNutritionProfile.Sex sex,
            Integer ageYears,
            Integer heightCm,
            BigDecimal currentWeightKg,
            UserNutritionProfile.ActivityLevel activityLevel,
            UserNutritionProfile.Goal goal,
            Integer waterTargetMlPerDay
    ) throws UserNutritionProfileValidationException{

        UserNutritionProfile profile = getOrCreateProfile(userId);

        if (sex != null) profile.setSex(sex);
        if (ageYears != null) profile.setAgeYears(ageYears);
        if (heightCm != null) profile.setHeightCm(heightCm);
        if (currentWeightKg != null) profile.setCurrentWeightKg(currentWeightKg);
        if (activityLevel != null) profile.setActivityLevel(activityLevel);
        if (goal != null) profile.setGoal(goal);
        if (waterTargetMlPerDay != null) profile.setWaterTargetMlPerDay(waterTargetMlPerDay);

        return validateAndSave(profile);
    }
}
