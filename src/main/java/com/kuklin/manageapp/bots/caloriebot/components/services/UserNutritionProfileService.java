package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UserNutritionProfileRepository;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.InvalidAgeException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.InvalidCaloriesNormException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.InvalidHeightException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.InvalidWeightException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserNutritionProfileDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
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

    public UserNutritionProfile updateCaloriesNorms(Long userId, Integer calories, Integer water) {
        UserNutritionProfile profile = getOrCreateProfile(userId);

        if (calories != null) {
            profile.setCaloriesNormPerDay(calories);
        } else {
            return null;
        }

        // Сохраняем напрямую в репозиторий, минуя метод recalculateAndSave,
        // который вызывает recalcTargets() и затирает всё формулами
        UserNutritionProfile saved = userNutritionProfileRepository.save(profile);
        userNutritionProfileEntryService.syncWithProfile(saved);

        return saved;
    }

    public UserNutritionProfile updateWaterNorms(Long userId, Integer water) {
        UserNutritionProfile profile = getOrCreateProfile(userId);

        if (water != null) {
            profile.setWaterTargetMlPerDay(water);
        } else {
            return null;
        }

        // Сохраняем напрямую в репозиторий, минуя метод recalculateAndSave,
        // который вызывает recalcTargets() и затирает всё формулами
        UserNutritionProfile saved = userNutritionProfileRepository.save(profile);

        // Важно: обновляем текущие записи (Entry), чтобы в отчетах за сегодня
        // сразу отобразилась новая норма
        userNutritionProfileEntryService.syncWithProfile(saved);

        return saved;
    }

    @Transactional
    public UserNutritionProfileDto getOrCreateProfileDto(Long userId) {
        return UserNutritionProfileDto.fromEntity(getOrCreateProfile(userId));
    }

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

    /*
     * Журнал веса — источник правды, вес в профиле = последнее взвешивание.
     * Бот пишет вес через профиль (CurrentWeightProfileEditFieldHandler → updateCurrentWeight),
     * API — через журнал (PUT /weight) или профиль (PUT /profile); все пути держат их в синхроне.
     */

    /**
     * Записать взвешивание: запись в журнал + текущий вес профиля + пересчёт нормы (если профиль заполнен).
     */
    @Transactional
    public WeightEntry logWeight(Long userId, BigDecimal weightKg) {
        UserNutritionProfile profile = getOrCreateProfile(userId);
        profile.setCurrentWeightKg(weightKg);
        // Сначала профиль: недопустимый вес отклоняется до записи в журнал
        saveRecalculatedIfPossible(profile);
        return weightEntryService.updateWeight(userId, weightKg);
    }

    /**
     * Удалить взвешивание. Если удалили последнее — вес профиля откатывается к предыдущему,
     * норма пересчитывается. Пустой журнал профиль не трогает.
     */
    @Transactional
    public void deleteWeightEntry(Long userId, Long weightId) {
        weightEntryService.deleteWeightEntryById(userId, weightId);

        WeightEntry latest = weightEntryService.getLatestOrNull(userId);
        if (latest == null) return;

        UserNutritionProfile profile = getOrCreateProfile(userId);
        if (sameWeight(profile.getCurrentWeightKg(), latest.getWeight())) return;

        profile.setCurrentWeightKg(latest.getWeight());
        saveRecalculatedIfPossible(profile);
    }

    // Пересчитать норму, если хватает данных, иначе просто сохранить (профиль ещё заполняется)
    private UserNutritionProfile saveRecalculatedIfPossible(UserNutritionProfile profile) {
        try {
            return checkTargetCalculateParams(profile)
                    ? recalculateAndSave(profile)
                    : validateAndSave(profile);
        } catch (InsufficientProfileDataException | UserNutritionProfileValidationException e) {
            throw new ErrorResponseException(ErrorStatus.USER_NUTRITION_PROFILE_VALIDATION_EXCEPTION);
        }
    }

    private static boolean sameWeight(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
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

        Integer calories = profile.getCaloriesNormPerDay();
        if (profile.isManualNorm() && calories != null
                && calories < CALORIES_NORM_MIN) {
            throw new InvalidCaloriesNormException(calories);
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
        UserNutritionProfileDto dto = recalcTargets(profile);
        profile = UserNutritionProfileDto.updateNutritionData(profile, dto);
        profile = validateAndSave(profile);
        userNutritionProfileEntryService.syncWithProfile(profile);
        return profile;
    }

    /**
     * Пересчёт нормы калорий, БЖУ и воды.
     * AUTO — калории по формуле; MANUAL — калории пользователя, БЖУ считаются от них.
     */
    private UserNutritionProfileDto recalcTargets(UserNutritionProfile profile)
            throws InsufficientProfileDataException {

        int caloriesTarget;
        if (profile.isManualNorm()) {
            if (profile.getCaloriesNormPerDay() == null)
                throw new InsufficientProfileDataException("Норма калорий");
            caloriesTarget = profile.getCaloriesNormPerDay();
        } else {
            checkTargetCalculateParamsOrThrow(profile);
            caloriesTarget = calcFormulaCalories(profile);
        }

        BigDecimal weightKg = profile.getCurrentWeightKg();

        int proteins;
        if (weightKg != null && profile.getGoal() != null) {
            // --- Proteins (от текущего веса — нормально даже при похудении) ---
            proteins = (int) Math.round(weightKg.doubleValue() * profile.getGoal().getProteinsPerKg());
        } else {
            // Ручная норма без веса/цели: белки — 30% калорий
            proteins = (int) Math.round(caloriesTarget * 0.30 / 4);
        }

        // --- Fats (процент от калорий, а не от веса) ---
        // 20–30% — норма, берём 25%
        int fatsCalories = (int) Math.round(caloriesTarget * 0.25);
        int fats = fatsCalories / 9;

        // --- Carbs (остаток; при маленькой ручной норме белок может съесть всё — не уходим в минус) ---
        int carbs = Math.max(0, (caloriesTarget - proteins * 4 - fats * 9) / 4);

        // --- Water --- (без веса/активности — оставляем как было)
        Integer waterTarget = weightKg != null && profile.getActivityLevel() != null
                ? (int) Math.round(weightKg.doubleValue() * profile.getActivityLevel().getWaterMlPerKg())
                : profile.getWaterTargetMlPerDay();

        return new UserNutritionProfileDto()
                .setCaloriesNormPerDay(caloriesTarget)
                .setProteinsNormGramsPerDay(proteins)
                .setFatsNormGramsPerDay(fats)
                .setCarbsNormGramsPerDay(Math.max(carbs, 0))
                .setWaterTargetMlPerDay(waterTarget);
    }

    /**
     * Норма калорий по формуле (Mifflin–St Jeor × активность × цель) — и для AUTO,
     * и как подсказка «по формуле» в ручном режиме.
     */
    private static int calcFormulaCalories(UserNutritionProfile profile) {
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

        return (int) Math.round(bmrCalories);
    }

    /** Норма калорий по формуле или null, если профиль не заполнен. */
    public Integer calcFormulaCaloriesOrNull(UserNutritionProfile profile) {
        return profile != null && profile.checkTargetCalculateParams() ? calcFormulaCalories(profile) : null;
    }

    //Хватает ли данных для нормы: AUTO — заполнен профиль, MANUAL — задана норма калорий
    public boolean checkTargetCalculateParams(UserNutritionProfile profile) {
        if (profile != null && profile.isManualNorm()) {
            return profile.getCaloriesNormPerDay() != null;
        }
        try {
            checkTargetCalculateParamsOrThrow(profile);
            return true;
        } catch (InsufficientProfileDataException e) {
            return false;
        }
    }

    /*
     * Ручная норма включается так: бот — кнопка «Норма калорий» (CaloriesNormProfileEditFieldHandler),
     * API — PUT /profile с normMode=MANUAL и caloriesNormPerDay. Дальше recalculateAndSave держит калории,
     * БЖУ считает от них, вода — как в AUTO.
     */

    /** Норма по формуле без сохранения — показать в боте, что изменится при включении автопересчёта. */
    public UserNutritionProfileDto previewAutoNorm(UserNutritionProfile profile)
            throws InsufficientProfileDataException {
        return recalcTargets(profile.copy().setNormMode(UserNutritionProfile.NormMode.AUTO));
    }

    /** Выключить автопересчёт: текущие калории становятся ручными (бот: «Без пересчёта»). */
    @Transactional
    public UserNutritionProfile switchToManualNorm(UserNutritionProfile profile)
            throws UserNutritionProfileValidationException {
        profile.setNormMode(UserNutritionProfile.NormMode.MANUAL);
        return validateAndSave(profile);
    }

    /** Вернуть норму по формуле (бот: «Автопересчёт» после подтверждения). */
    @Transactional
    public UserNutritionProfile switchToAutoNorm(UserNutritionProfile profile)
            throws UserNutritionProfileValidationException, InsufficientProfileDataException {
        profile.setNormMode(UserNutritionProfile.NormMode.AUTO);
        return recalculateAndSave(profile);
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


    @Transactional
    public UserNutritionProfile patchProfile(UserNutritionProfile userNutritionProfile) throws UserNutritionProfileValidationException {
        if (userNutritionProfile.getUserId() == null) return null;
        userNutritionProfile = validateAndSave(userNutritionProfile);
        userNutritionProfileEntryService.syncWithProfile(userNutritionProfile);
        return userNutritionProfile;
    }

    @Transactional
    public UserNutritionProfileDto patchProfileDto(Long userId, UserNutritionProfileDto dto) {
        try {
            UserNutritionProfile existing = getOrCreateProfile(userId);   // ← грузим то, что уже есть
            BigDecimal oldWeight = existing.getCurrentWeightKg();         // mergeToEntity меняет existing
            UserNutritionProfile merged = dto.mergeToEntity(existing);    // ← мёржим, а не создаём заново
            UserNutritionProfile saved = recalculateAndSave(merged);
            // Новый вес из профиля — тоже взвешивание, как в боте
            if (dto.getCurrentWeightKg() != null && !sameWeight(oldWeight, dto.getCurrentWeightKg())) {
                weightEntryService.updateWeight(userId, dto.getCurrentWeightKg());
            }
            return UserNutritionProfileDto.fromEntity(saved);
        } catch (InsufficientProfileDataException e) {
            throw new ErrorResponseException(ErrorStatus.PROFILE_INSUFFICIENT_DATA);
        } catch (UserNutritionProfileValidationException e) {
            throw new ErrorResponseException(ErrorStatus.USER_NUTRITION_PROFILE_VALIDATION_EXCEPTION);
        }
    }

//    /**
//     * PATCH-подобное обновление профиля.
//     * null-поля игнорируются.
//     * Метод либо возвращает валидный профиль,
//     * либо кидает UserNutritionProfileException.
//     */
//    @Transactional
//    public UserNutritionProfile patchProfile(
//            Long userId,
//            UserNutritionProfile.Sex sex,
//            Integer ageYears,
//            Integer heightCm,
//            BigDecimal currentWeightKg,
//            UserNutritionProfile.ActivityLevel activityLevel,
//            UserNutritionProfile.Goal goal,
//            Integer waterTargetMlPerDay,
//            DietType dietType
//    ) throws UserNutritionProfileValidationException {
//
//        UserNutritionProfile profile = getOrCreateProfile(userId);
//
//        if (sex != null) profile.setSex(sex);
//        if (ageYears != null) profile.setAgeYears(ageYears);
//        if (heightCm != null) profile.setHeightCm(heightCm);
//        if (currentWeightKg != null) profile.setCurrentWeightKg(currentWeightKg);
//        if (activityLevel != null) profile.setActivityLevel(activityLevel);
//        if (goal != null) profile.setGoal(goal);
//        if (waterTargetMlPerDay != null) profile.setWaterTargetMlPerDay(waterTargetMlPerDay);
//        if (dietType != null) profile.setDietType(dietType);
//
//        profile = validateAndSave(profile);
//        userNutritionProfileEntryService.syncWithProfile(profile);
//        return profile;
//    }
}
