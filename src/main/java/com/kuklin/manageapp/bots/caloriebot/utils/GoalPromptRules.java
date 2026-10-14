package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.DietType;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.Goal;

import java.util.Map;

/**
 * Правила под цель и тип питания — подставляются во все промпты с профилем.
 * Одной фразы «учитывай цель» модели мало: она по умолчанию считает, что все худеют,
 * и ругает за перебор того, кто набирает вес. Поэтому код говорит прямо, что для этой цели хорошо и что плохо.
 */
public final class GoalPromptRules {

    static final Map<Goal, String> GOAL_NAMES = Map.of(
            Goal.LOSE_WEIGHT, "похудение",
            Goal.MAINTAIN, "поддержание веса",
            Goal.GAIN_WEIGHT, "набор веса"
    );

    static final Map<DietType, String> DIET_NAMES = Map.of(
            DietType.REGULAR, "обычное",
            DietType.LOW_CARB, "низкоуглеводное",
            DietType.VEGAN, "веганское"
    );

    private static final String NO_GOAL_RULES =
            """
                    Цель: не указана. Оценивай относительно нормы из профиля и не предполагай, что пользователь худеет.""";

    private static final Map<Goal, String> GOAL_RULES = Map.of(
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
    );

    private static final Map<DietType, String> DIET_RULES = Map.of(
            DietType.LOW_CARB,
            """
                    Тип питания: низкоуглеводное. Норма углеводов снижена намеренно — недобор углеводов не проблема.
                    Не советуй хлеб, крупы, сладкие фрукты и сладкое; советы и замены — белок, овощи, полезные жиры.""",
            DietType.VEGAN,
            """
                    Тип питания: веганское. Никогда не советуй продукты животного происхождения: мясо, рыбу, яйца, молочное, мёд.
                    Белок — бобовые, тофу, темпе, сейтан, орехи. Если пользователь всё же ел животное — не осуждай, просто советуй веганское."""
    );

    private GoalPromptRules() {
    }

    /** Блок правил для промпта: цель всегда, тип питания — если он что-то ограничивает. */
    public static String buildRules(UserNutritionProfile profile) {
        String goalRules = profile.getGoal() != null ? GOAL_RULES.get(profile.getGoal()) : NO_GOAL_RULES;
        String dietRules = profile.getDietType() != null ? DIET_RULES.get(profile.getDietType()) : null;
        return dietRules != null ? goalRules + "\n" + dietRules : goalRules;
    }

    public static String getGoalName(Goal goal) {
        return GOAL_NAMES.get(goal);
    }

    public static String getDietName(DietType diet) {
        return DIET_NAMES.get(diet);
    }
}
