package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.Goal;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;

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

/**
 * Промпт дневного ИИ-отчёта в боте.
 * Формат вступления и тему совета на завтра выбирает код случайно — иначе модель каждый день
 * начинала с одной и той же «новости». Итоги дня тоже считает код: складывать калории модель ошибается.
 * <p>
 * Весь промпт — инструкция, подписи данных, дата — на языке промпта ({@link ReportPrompts#of}):
 * когда вокруг английского ответа всё было по-русски, модель вставляла русские слова.
 */
public final class DayReportPrompt {

    /** Тема совета и цели, которым она подходит: «есть медленнее» тому, кто набирает вес, вредит. */
    record TipTheme(String text, Set<Goal> goals) {
    }

    private static final Set<Goal> ALL_GOALS = EnumSet.allOf(Goal.class);
    private static final Set<Goal> NOT_GAIN = EnumSet.of(Goal.LOSE_WEIGHT, Goal.MAINTAIN);
    private static final Set<Goal> GAIN = EnumSet.of(Goal.GAIN_WEIGHT);

    /**
     * Тексты промпта на одном языке. Списки вступлений и тем — по порядку одни и те же в обоих языках
     * (ReportPromptsConsistencyTest сверяет число и цели тем).
     */
    record Texts(List<String> introFormats,
                 List<TipTheme> tipThemes,
                 // Дата для модели: «среда, 14 октября 2026, 21:05» / «Wednesday, 14 October 2026, 21:05»
                 DateTimeFormatter userTimeFormat,
                 String sex, String age, String height, String weight, String activity, String goal, String diet,
                 String centimeter, String kilogram, String profileEmpty,
                 String calories, String proteins, String fats, String carbs, String kcal, String gram,
                 String of, String left, String over, String noTarget,
                 String nothingEaten,
                 String dishMacrosFormat) {
    }

    static final Texts RU = new Texts(
            List.of(
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
            ),
            List.of(
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
            ),
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru")),
            "пол", "возраст", "рост", "вес", "активность", "цель", "питание",
            "см", "кг", "не заполнен",
            "калории", "белки", "жиры", "углеводы", "ккал", "г",
            "из", "осталось", "перебор", "норма не задана",
            "ничего",
            "%d ккал, Б %d / Ж %d / У %d"
    );

    static final Texts EN = new Texts(
            List.of(
                    "a weather forecast, but for the stomach and metabolism",
                    "a sports commentator covering the deciding match",
                    "a stock market report: quotes for the foods in today's menu",
                    "an evening horoscope for the sign \"Eater\"",
                    "an announcement from the airplane captain to the passengers",
                    "a wildlife documentary voice-over — the hero is at the fridge",
                    "a blockbuster trailer voice: \"In a world where…\"",
                    "a strict restaurant critic's review of today",
                    "a quiz question for the listeners on live air",
                    "a traffic report, but for digestion",
                    "breaking news from the land of Body — obviously fictional",
                    "a listener calling in on live air",
                    "a train station PA announcement",
                    "a radio ad in the style of \"Tired of…? Then…\"",
                    "a hit parade: top dishes of the day",
                    "a reliable, well-known fact about a food from today's menu, no made-up numbers",
                    "a report from the fields: how the calorie harvest is going",
                    "a forecaster's outlook for tomorrow, but about energy and mood"
            ),
            List.of(
                    new TipTheme("water and drinks", ALL_GOALS),
                    new TipTheme("sleep", ALL_GOALS),
                    new TipTheme("protein at breakfast", ALL_GOALS),
                    new TipTheme("fiber and vegetables", ALL_GOALS),
                    new TipTheme("the order of foods within a meal", NOT_GAIN),
                    new TipTheme("spices and flavor without extra calories", NOT_GAIN),
                    new TipTheme("snacks", ALL_GOALS),
                    new TipTheme("dinner time", ALL_GOALS),
                    new TipTheme("a walk or steps after eating", ALL_GOALS),
                    new TipTheme("food prepared in advance", ALL_GOALS),
                    new TipTheme("eating slower and without a screen", NOT_GAIN),
                    new TipTheme("sweets without binges", NOT_GAIN),
                    new TipTheme("coffee and caffeine", ALL_GOALS),
                    new TipTheme("a shopping list", ALL_GOALS),
                    new TipTheme("calorie-dense healthy add-ons: nuts, nut butter, oil", GAIN),
                    new TipTheme("calories in drinks: smoothies, shakes", GAIN),
                    new TipTheme("more meals instead of huge portions", GAIN)
            ),
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH),
            "sex", "age", "height", "weight", "activity", "goal", "diet",
            "cm", "kg", "not filled in",
            "calories", "protein", "fat", "carbs", "kcal", "g",
            "of", "left", "over by", "no target set",
            "nothing",
            "%d kcal, P %d / F %d / C %d"
    );

    private static final DateTimeFormatter DISH_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private DayReportPrompt() {
    }

    static Texts getTexts(AppLanguage language) {
        return language == AppLanguage.EN ? EN : RU;
    }

    /**
     * @param dishes   блюда за сегодня
     * @param userTime текущее время в таймзоне пользователя — по ней же время блюд
     * @param language язык ответа: по нему выбираются промпт, подписи данных и строка «язык ответа»
     */
    public static String buildPrompt(UserNutritionProfile profile, List<Dish> dishes, ZonedDateTime userTime,
                                     AppLanguage language, RandomGenerator random) {
        Texts texts = getTexts(language);
        ReportPrompts prompts = ReportPrompts.of(language);
        List<String> tipThemes = getTipThemes(profile.getGoal(), language);
        return prompts.persona() + String.format(prompts.todayAnalysis(),
                buildProfileText(profile, language),
                buildTotalsText(profile, dishes, language),
                buildDishesText(dishes, userTime, language),
                userTime.format(texts.userTimeFormat()),
                language.getNativeName(),
                texts.introFormats().get(random.nextInt(texts.introFormats().size())),
                tipThemes.get(random.nextInt(tipThemes.size())),
                GoalPromptRules.buildRules(profile, language));
    }

    /** Темы советов под цель; цель не указана — как для поддержания. */
    static List<String> getTipThemes(Goal goal, AppLanguage language) {
        Goal themeGoal = goal != null ? goal : Goal.MAINTAIN;
        return getTexts(language).tipThemes().stream()
                .filter(theme -> theme.goals().contains(themeGoal))
                .map(TipTheme::text)
                .toList();
    }

    /** Только то, что нужно для совета: без id, режимов норм и состояния заполнения. */
    static String buildProfileText(UserNutritionProfile profile, AppLanguage language) {
        Texts texts = getTexts(language);
        List<String> parts = new ArrayList<>();
        if (profile.getSex() != null) parts.add(texts.sex() + " " + profile.getSex());
        if (profile.getAgeYears() != null) parts.add(texts.age() + " " + profile.getAgeYears());
        if (profile.getHeightCm() != null) parts.add(texts.height() + " " + profile.getHeightCm() + " " + texts.centimeter());
        if (profile.getCurrentWeightKg() != null) {
            parts.add(texts.weight() + " " + profile.getCurrentWeightKg().stripTrailingZeros().toPlainString()
                    + " " + texts.kilogram());
        }
        if (profile.getActivityLevel() != null) parts.add(texts.activity() + " " + profile.getActivityLevel());
        if (profile.getGoal() != null) {
            parts.add(texts.goal() + " " + GoalPromptRules.getGoalName(profile.getGoal(), language));
        }
        if (profile.getDietType() != null) {
            parts.add(texts.diet() + " " + GoalPromptRules.getDietName(profile.getDietType(), language));
        }
        return parts.isEmpty() ? texts.profileEmpty() : String.join(", ", parts);
    }

    static String buildTotalsText(UserNutritionProfile profile, List<Dish> dishes, AppLanguage language) {
        Texts texts = getTexts(language);
        return String.join("; ",
                buildTotalText(texts, texts.calories(), texts.kcal(), sum(dishes, Dish::getCalories), profile.getCaloriesNormPerDay()),
                buildTotalText(texts, texts.proteins(), texts.gram(), sum(dishes, Dish::getProteins), profile.getProteinsNormGramsPerDay()),
                buildTotalText(texts, texts.fats(), texts.gram(), sum(dishes, Dish::getFats), profile.getFatsNormGramsPerDay()),
                buildTotalText(texts, texts.carbs(), texts.gram(), sum(dishes, Dish::getCarbohydrates), profile.getCarbsNormGramsPerDay()));
    }

    /** «калории 1840 из 2100 ккал, осталось 260» / «… перебор 150» / «… норма не задана». */
    private static String buildTotalText(Texts texts, String name, String unit, int eaten, Integer target) {
        if (target == null || target <= 0) {
            return name + " " + eaten + " " + unit + ", " + texts.noTarget();
        }
        int left = target - eaten;
        String rest = left >= 0 ? texts.left() + " " + left : texts.over() + " " + (-left);
        return name + " " + eaten + " " + texts.of() + " " + target + " " + unit + ", " + rest;
    }

    /** По строке на блюдо: «08:30 — Овсянка, 250 г: 350 ккал, Б 12 / Ж 8 / У 55». */
    static String buildDishesText(List<Dish> dishes, ZonedDateTime userTime, AppLanguage language) {
        Texts texts = getTexts(language);
        if (dishes == null || dishes.isEmpty()) {
            return texts.nothingEaten();
        }
        return dishes.stream()
                .map(dish -> buildDishLine(dish, userTime, texts))
                .collect(Collectors.joining("\n"));
    }

    private static String buildDishLine(Dish dish, ZonedDateTime userTime, Texts texts) {
        String time = dish.getCreated() != null
                ? dish.getCreated().atZone(userTime.getZone()).format(DISH_TIME_FORMAT) + " — "
                : "";
        String weight = dish.getWeight() != null ? ", " + dish.getWeight() + " " + texts.gram() : "";
        return time + dish.getName() + weight + ": " + String.format(texts.dishMacrosFormat(),
                orZero(dish.getCalories()), orZero(dish.getProteins()), orZero(dish.getFats()), orZero(dish.getCarbohydrates()));
    }

    private static int sum(List<Dish> dishes, Function<Dish, Integer> value) {
        return dishes == null ? 0 : dishes.stream().mapToInt(dish -> orZero(value.apply(dish))).sum();
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }
}
