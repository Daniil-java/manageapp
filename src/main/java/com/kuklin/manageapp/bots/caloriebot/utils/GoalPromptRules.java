package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.DietType;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.Goal;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;

import java.util.Map;

/**
 * Правила под цель и тип питания — подставляются во все промпты с профилем.
 * Одной фразы «учитывай цель» модели мало: она по умолчанию считает, что все худеют,
 * и ругает за перебор того, кто набирает вес. Поэтому код говорит прямо, что для этой цели хорошо и что плохо.
 * <p>
 * Тексты на языке промпта ({@link ReportPrompts#of}): английские для EN, русские для остальных.
 */
public final class GoalPromptRules {

    /** Тексты правил на одном языке. */
    record Texts(Map<Goal, String> goalNames,
                 Map<DietType, String> dietNames,
                 String noGoalRules,
                 Map<Goal, String> goalRules,
                 Map<DietType, String> dietRules) {
    }

    static final Texts RU = new Texts(
            Map.of(
                    Goal.LOSE_WEIGHT, "похудение",
                    Goal.MAINTAIN, "поддержание веса",
                    Goal.GAIN_WEIGHT, "набор веса"
            ),
            Map.of(
                    DietType.REGULAR, "обычное",
                    DietType.LOW_CARB, "низкоуглеводное",
                    DietType.VEGAN, "веганское"
            ),
            """
                    Цель: не указана. Оценивай относительно нормы из профиля и не предполагай, что пользователь худеет.""",
            Map.of(
                    Goal.LOSE_WEIGHT,
                    """
                            Цель: похудение.
                            - Хорошо: калории около нормы или немного ниже, белок в норме, вес снижается.
                            - Перебор калорий мешает цели. Сильный недобор (ниже нормы больше чем на 25%) — тоже плохо: голод, срывы, потеря мышц.
                            - Советы — про сытость при меньших калориях: белок, овощи, вода. Без голодовок.""",
                    Goal.MAINTAIN,
                    """
                            Цель: поддержание веса.
                            - Хорошо: калории в коридоре нормы ±10%, вес стабилен (±0,5 кг).
                            - Перебор и недобор одинаково уводят от цели — оценивай отклонение в обе стороны, без уклона в похудение.
                            - Советы — про регулярность и баланс, а не про урезание.""",
                    Goal.GAIN_WEIGHT,
                    """
                            Цель: набор веса.
                            - Хорошо: калории на уровне нормы или немного выше, белок в норме, вес растёт. Хвали, когда норма закрыта.
                            - Главный риск — недобор калорий: подскажи, чем добрать.
                            - Перебор до 15% — не проблема: не ругай за него и не советуй «закрыть кухню».
                            - Не советуй урезать порции, есть медленнее или менять еду на менее калорийную.
                              Советы — калорийная полезная еда: орехи и ореховая паста, масло в салат, крупы, перекусы между приёмами пищи."""
            ),
            Map.of(
                    DietType.LOW_CARB,
                    """
                            Тип питания: низкоуглеводное. Норма углеводов снижена намеренно — недобор углеводов не проблема.
                            Не советуй хлеб, крупы, сладкие фрукты и сладкое; советы и замены — белок, овощи, полезные жиры.""",
                    DietType.VEGAN,
                    """
                            Тип питания: веганское. Никогда не советуй продукты животного происхождения: мясо, рыбу, яйца, молочное, мёд.
                            Белок — бобовые, тофу, темпе, сейтан, орехи. Если пользователь всё же ел животное — не осуждай, просто советуй веганское."""
            )
    );

    static final Texts EN = new Texts(
            Map.of(
                    Goal.LOSE_WEIGHT, "weight loss",
                    Goal.MAINTAIN, "weight maintenance",
                    Goal.GAIN_WEIGHT, "weight gain"
            ),
            Map.of(
                    DietType.REGULAR, "regular",
                    DietType.LOW_CARB, "low-carb",
                    DietType.VEGAN, "vegan"
            ),
            """
                    Goal: not set. Judge against the target from the profile and don't assume the user is losing weight.""",
            Map.of(
                    Goal.LOSE_WEIGHT,
                    """
                            Goal: weight loss.
                            - Good: calories around the target or slightly below, protein on target, weight going down.
                            - A calorie surplus works against the goal. A big deficit (more than 25% below the target) is bad too: hunger, binges, muscle loss.
                            - Tips — about staying full on fewer calories: protein, vegetables, water. No starving.""",
                    Goal.MAINTAIN,
                    """
                            Goal: weight maintenance.
                            - Good: calories within ±10% of the target, weight stable (±0.5 kg).
                            - Surplus and deficit lead away from the goal equally — judge deviation both ways, without leaning towards weight loss.
                            - Tips — about regularity and balance, not about cutting down.""",
                    Goal.GAIN_WEIGHT,
                    """
                            Goal: weight gain.
                            - Good: calories at the target or slightly above, protein on target, weight going up. Praise when the target is reached.
                            - The main risk is a calorie deficit: suggest what to add.
                            - A surplus up to 15% is not a problem: don't scold for it and don't suggest "closing the kitchen".
                            - Don't suggest cutting portions, eating slower or swapping food for lower-calorie options.
                              Tips — calorie-dense healthy food: nuts and nut butter, oil in salads, grains, snacks between meals."""
            ),
            Map.of(
                    DietType.LOW_CARB,
                    """
                            Diet type: low-carb. The carb target is lowered on purpose — a carb deficit is not a problem.
                            Don't suggest bread, grains, sweet fruit or sweets; tips and swaps — protein, vegetables, healthy fats.""",
                    DietType.VEGAN,
                    """
                            Diet type: vegan. Never suggest animal products: meat, fish, eggs, dairy, honey.
                            Protein — legumes, tofu, tempeh, seitan, nuts. If the user still ate something animal — don't judge, just suggest vegan options."""
            )
    );

    private GoalPromptRules() {
    }

    /** Блок правил для промпта: цель всегда, тип питания — если он что-то ограничивает. */
    public static String buildRules(UserNutritionProfile profile, AppLanguage language) {
        Texts texts = getTexts(language);
        String goalRules = profile.getGoal() != null ? texts.goalRules().get(profile.getGoal()) : texts.noGoalRules();
        String dietRules = profile.getDietType() != null ? texts.dietRules().get(profile.getDietType()) : null;
        return dietRules != null ? goalRules + "\n" + dietRules : goalRules;
    }

    public static String getGoalName(Goal goal, AppLanguage language) {
        return getTexts(language).goalNames().get(goal);
    }

    public static String getDietName(DietType diet, AppLanguage language) {
        return getTexts(language).dietNames().get(diet);
    }

    static Texts getTexts(AppLanguage language) {
        return language == AppLanguage.EN ? EN : RU;
    }
}
