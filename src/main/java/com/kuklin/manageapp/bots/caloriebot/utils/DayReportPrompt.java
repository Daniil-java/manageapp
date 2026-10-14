package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.Goal;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import static com.kuklin.manageapp.bots.caloriebot.utils.ReportCalorieBotPrompts.AI_PERSONA;
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportCalorieBotPrompts.AI_REQUEST_TODAY_ANALYSIS;

/**
 * Промпт дневного ИИ-отчёта в боте.
 * Формат вступления и тему совета на завтра выбирает код случайно — иначе модель каждый день
 * начинала с одной и той же «новости». Итоги дня тоже считает код: складывать калории модель ошибается.
 */
public final class DayReportPrompt {

    static final List<String> INTRO_FORMATS = List.of(
            "прогноз погоды, но для желудка и метаболизма",
            "репортаж спортивного комментатора с решающего матча",
            "сводка с биржи: котировки продуктов из сегодняшнего рациона",
            "гороскоп на вечер для знака «Едок»",
            "объявление командира воздушного судна пассажирам",
            "закадровый голос документального фильма о дикой природе — герой у холодильника",
            "голос из трейлера блокбастера: «В мире, где…»",
            "рецензия строгого ресторанного критика на сегодняшний день",
            "вопрос викторины слушателям в прямом эфире",
            "сводка о пробках, только в пищеварении",
            "экстренный выпуск новостей из страны Организм — явно вымышленный",
            "звонок слушателя в прямой эфир",
            "объявление по громкой связи на вокзале",
            "радиореклама в стиле «Устали от…? Тогда…»",
            "хит-парад: топ блюд сегодняшнего дня",
            "достоверный общеизвестный факт о продукте из сегодняшнего рациона, без выдуманных цифр",
            "сводка с полей: как идёт уборка урожая калорий",
            "прогноз от синоптика на завтра, но про энергию и настроение"
    );

    /** Тема совета и цели, которым она подходит: «есть медленнее» тому, кто набирает вес, вредит. */
    record TipTheme(String text, Set<Goal> goals) {
    }

    private static final Set<Goal> ALL_GOALS = EnumSet.allOf(Goal.class);
    private static final Set<Goal> NOT_GAIN = EnumSet.of(Goal.LOSE_WEIGHT, Goal.MAINTAIN);
    private static final Set<Goal> GAIN = EnumSet.of(Goal.GAIN_WEIGHT);

    static final List<TipTheme> TIP_THEMES = List.of(
            new TipTheme("вода и напитки", ALL_GOALS),
            new TipTheme("сон", ALL_GOALS),
            new TipTheme("белок в завтраке", ALL_GOALS),
            new TipTheme("клетчатка и овощи", ALL_GOALS),
            new TipTheme("порядок блюд в приёме пищи", NOT_GAIN),
            new TipTheme("специи и вкус без лишних калорий", NOT_GAIN),
            new TipTheme("перекусы", ALL_GOALS),
            new TipTheme("время ужина", ALL_GOALS),
            new TipTheme("прогулка или шаги после еды", ALL_GOALS),
            new TipTheme("еда, приготовленная заранее", ALL_GOALS),
            new TipTheme("есть медленнее и без экрана", NOT_GAIN),
            new TipTheme("сладкое без срывов", NOT_GAIN),
            new TipTheme("кофе и кофеин", ALL_GOALS),
            new TipTheme("список покупок", ALL_GOALS),
            new TipTheme("калорийные полезные добавки: орехи, ореховая паста, масло", GAIN),
            new TipTheme("калории в напитках: смузи, коктейли", GAIN),
            new TipTheme("больше приёмов пищи вместо огромных порций", GAIN)
    );

    // Дату модель видит по-русски, как и всю инструкцию: «среда, 14 октября 2026, 21:05»
    private static final DateTimeFormatter USER_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter DISH_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private DayReportPrompt() {
    }

    /**
     * @param dishes   блюда за сегодня
     * @param userTime текущее время в таймзоне пользователя — по ней же время блюд
     * @param language язык ответа ({@link com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage#getNativeName()})
     */
    public static String buildPrompt(UserNutritionProfile profile, List<Dish> dishes, ZonedDateTime userTime,
                                     String language, RandomGenerator random) {
        List<String> tipThemes = getTipThemes(profile.getGoal());
        return AI_PERSONA + String.format(AI_REQUEST_TODAY_ANALYSIS,
                buildProfileText(profile),
                buildTotalsText(profile, dishes),
                buildDishesText(dishes, userTime),
                userTime.format(USER_TIME_FORMAT),
                language,
                INTRO_FORMATS.get(random.nextInt(INTRO_FORMATS.size())),
                tipThemes.get(random.nextInt(tipThemes.size())),
                GoalPromptRules.buildRules(profile));
    }

    /** Темы советов под цель; цель не указана — как для поддержания. */
    static List<String> getTipThemes(Goal goal) {
        Goal themeGoal = goal != null ? goal : Goal.MAINTAIN;
        return TIP_THEMES.stream()
                .filter(theme -> theme.goals().contains(themeGoal))
                .map(TipTheme::text)
                .toList();
    }

    /** Только то, что нужно для совета: без id, режимов норм и состояния заполнения. */
    static String buildProfileText(UserNutritionProfile profile) {
        List<String> parts = new ArrayList<>();
        if (profile.getSex() != null) parts.add("пол " + profile.getSex());
        if (profile.getAgeYears() != null) parts.add("возраст " + profile.getAgeYears());
        if (profile.getHeightCm() != null) parts.add("рост " + profile.getHeightCm() + " см");
        if (profile.getCurrentWeightKg() != null) {
            parts.add("вес " + profile.getCurrentWeightKg().stripTrailingZeros().toPlainString() + " кг");
        }
        if (profile.getActivityLevel() != null) parts.add("активность " + profile.getActivityLevel());
        if (profile.getGoal() != null) parts.add("цель " + GoalPromptRules.getGoalName(profile.getGoal()));
        if (profile.getDietType() != null) parts.add("питание " + GoalPromptRules.getDietName(profile.getDietType()));
        return parts.isEmpty() ? "не заполнен" : String.join(", ", parts);
    }

    static String buildTotalsText(UserNutritionProfile profile, List<Dish> dishes) {
        return String.join("; ",
                buildTotalText("калории", "ккал", sum(dishes, Dish::getCalories), profile.getCaloriesNormPerDay()),
                buildTotalText("белки", "г", sum(dishes, Dish::getProteins), profile.getProteinsNormGramsPerDay()),
                buildTotalText("жиры", "г", sum(dishes, Dish::getFats), profile.getFatsNormGramsPerDay()),
                buildTotalText("углеводы", "г", sum(dishes, Dish::getCarbohydrates), profile.getCarbsNormGramsPerDay()));
    }

    /** «калории 1840 из 2100 ккал, осталось 260» / «… перебор 150» / «… норма не задана». */
    private static String buildTotalText(String name, String unit, int eaten, Integer target) {
        if (target == null || target <= 0) {
            return name + " " + eaten + " " + unit + ", норма не задана";
        }
        int left = target - eaten;
        String rest = left >= 0 ? "осталось " + left : "перебор " + (-left);
        return name + " " + eaten + " из " + target + " " + unit + ", " + rest;
    }

    /** По строке на блюдо: «08:30 — Овсянка, 250 г: 350 ккал, Б 12 / Ж 8 / У 55». */
    static String buildDishesText(List<Dish> dishes, ZonedDateTime userTime) {
        if (dishes == null || dishes.isEmpty()) {
            return "ничего";
        }
        return dishes.stream()
                .map(dish -> buildDishLine(dish, userTime))
                .collect(Collectors.joining("\n"));
    }

    private static String buildDishLine(Dish dish, ZonedDateTime userTime) {
        String time = dish.getCreated() != null
                ? dish.getCreated().atZone(userTime.getZone()).format(DISH_TIME_FORMAT) + " — "
                : "";
        String weight = dish.getWeight() != null ? ", " + dish.getWeight() + " г" : "";
        return time + dish.getName() + weight + ": " + orZero(dish.getCalories()) + " ккал, Б " + orZero(dish.getProteins())
                + " / Ж " + orZero(dish.getFats()) + " / У " + orZero(dish.getCarbohydrates());
    }

    private static int sum(List<Dish> dishes, Function<Dish, Integer> value) {
        return dishes == null ? 0 : dishes.stream().mapToInt(dish -> orZero(value.apply(dish))).sum();
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }
}
