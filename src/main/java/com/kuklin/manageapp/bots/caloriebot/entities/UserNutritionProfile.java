package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.bots.caloriebot.services.exceptions.InsufficientProfileDataException;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_nutrition_profiles")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserNutritionProfile {

    //Возраст
    public static final Integer AGE_MIN = 0;
    public static final Integer AGE_MAX = 140;
    //Рост
    public static final Integer HEIGHT_MIN = 30;
    public static final Integer HEIGHT_MAX = 280;
    //Вес
    public static final Integer WEIGHT_MIN = 1;
    public static final Integer WEIGHT_MAX = 500;
    //Вода
    public static final Integer DEF_WATER_ML = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Integer ageYears;
    private Integer heightCm;
    @Column(name = "current_weight_kg", precision = 5, scale = 2)
    private BigDecimal currentWeightKg;
    @Enumerated(EnumType.STRING)
    private Sex sex;
    @Enumerated(EnumType.STRING)
    private ActivityLevel activityLevel;
    @Enumerated(EnumType.STRING)
    private Goal goal;
    @Enumerated(EnumType.STRING)
    private DietType dietType;
    @Enumerated(EnumType.STRING)
    private UserProfileFillingState userProfileFillingState;
    private Integer caloriesNormPerDay;
    private Integer proteinsNormGramsPerDay;
    private Integer fatsNormGramsPerDay;
    private Integer carbsNormGramsPerDay;
    private Integer waterTargetMlPerDay;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum UserProfileFillingState {
        EMPTY,      // только создан
        FILLING,    // процесс заполнения
        COMPLETED
    }

    public interface Labeled {
        String getLabel();
    }

    @Getter
    @RequiredArgsConstructor
    public enum DietType implements Labeled {
        REGULAR("Обычная"),
        LOW_CARB("Низкоуглеводная"),
        VEGAN("Веганская");

        private final String label;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Goal implements Labeled {
        LOSE_WEIGHT("Похудение", 0.8d, 2d, 0.9d),
        MAINTAIN("Поддержание", 1d, 1.8d, 0.9d),
        GAIN_WEIGHT("Набор веса", 1.15d, 1.8d, 0.9d);

        private final String label;
        private final Double coef;
        private final Double proteinsPerKg;
        private final Double fatsPerKg;
    }

    @Getter
    @RequiredArgsConstructor
    public enum ActivityLevel implements Labeled{
        LOW("Низкая", 1.2d, 30),
        MEDIUM("Средняя", 1.55d, 33),
        HIGH("Высокая", 1.75d, 35);

        private final String label;
        private final Double coef;
        private final Integer waterMlPerKg;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Sex implements Labeled{
        MALE("Мужской"),
        FEMALE("Женский");

        private final String label;
    }

    public String toTelegramView() {
        StringBuilder sb = new StringBuilder();

        sb.append("📋 Профиль питания\n");
        sb.append("──────────────────────\n\n");

        // Основные данные
        sb.append("👤 Пол: ")
                .append(formatSex())
                .append("\n");

        sb.append("🎂 Возраст: ")
                .append(formatInt(ageYears, v -> v + " лет"))
                .append("\n");

        sb.append("📏 Рост: ")
                .append(formatInt(heightCm, v -> v + " см"))
                .append("\n");

        sb.append("⚖️ Текущий вес: ")
                .append(formatBigDecimal(currentWeightKg, v -> v + " кг"))
                .append("\n");

        sb.append("🏃 Активность: ")
                .append(activityLevel == null ? "нет данных" : activityLevel.label)
                .append("\n");

        sb.append("🎯 Цель: ")
                .append(goal == null ? "нет данных" : goal.label)
                .append("\n");

        sb.append("🥗 Тип питания: ")
                .append(dietType == null ? "нет данных" : dietType.label)
                .append("\n");

        // Нормы по калориям и БЖУ
        sb.append("\n🔥 Норма калорий и БЖУ\n");

        boolean hasTargets =
                caloriesNormPerDay != null ||
                        proteinsNormGramsPerDay != null ||
                        fatsNormGramsPerDay != null ||
                        carbsNormGramsPerDay != null;

        if (!hasTargets) {
            sb.append("нет данных (нужно заполнить профиль)\n");
        } else {
            sb.append("• Калории: ")
                    .append(formatInt(caloriesNormPerDay, v -> v + " ккал"))
                    .append("\n");
            sb.append("• Белки: ")
                    .append(formatInt(proteinsNormGramsPerDay, v -> v + " г"))
                    .append("\n");
            sb.append("• Жиры: ")
                    .append(formatInt(fatsNormGramsPerDay, v -> v + " г"))
                    .append("\n");
            sb.append("• Углеводы: ")
                    .append(formatInt(carbsNormGramsPerDay, v -> v + " г"))
                    .append("\n");
        }

        // Вода
        sb.append("\n💧 Вода\n");
        if (waterTargetMlPerDay == null) {
            sb.append("Цель по воде: нет данных\n");
        } else {
            double liters = waterTargetMlPerDay / 1000.0;
            sb.append("Цель по воде: ")
                    .append(waterTargetMlPerDay)
                    .append(" мл (≈ ")
                    .append(String.format("%.1f", liters))
                    .append(" л)\n");
        }

        // Таймстемпы, если нужны
        if (updatedAt != null) {
            sb.append("\nПоследнее обновление: ")
                    .append(updatedAt.toLocalDate())
                    .append("\n");
        }

        return sb.toString();
    }

    private String formatSex() {
        if (sex == null) {
            return "нет данных";
        }
        switch (sex) {
            case MALE:
                return "Мужской ♂️";
            case FEMALE:
                return "Женский ♀️";
            default:
                return formatEnum(sex);
        }
    }

    private String formatEnum(Enum<?> e) {
        if (e == null) {
            return "нет данных";
        }
        // Преобразуем SNAKE_CASE → "Нормальный текст"
        String raw = e.name().toLowerCase().replace('_', ' ');
        if (raw.isEmpty()) {
            return "нет данных";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private String formatBigDecimal(BigDecimal value, java.util.function.Function<String, String> formatter) {
        if (value == null) {
            return "нет данных";
        }
        String plain = value.stripTrailingZeros().toPlainString();
        return formatter.apply(plain);
    }

    private String formatInt(Integer value, java.util.function.Function<Integer, String> formatter) {
        if (value == null) {
            return "нет данных";
        }
        return formatter.apply(value);
    }

    public Boolean checkTargetCalculateParams() {
        if (sex == null) return false;
        if (ageYears == null) return false;
        if (heightCm == null) return false;
        if (currentWeightKg == null) return false;
        if (activityLevel == null) return false;
        if (goal == null) return false;

        return true;
    }
}
