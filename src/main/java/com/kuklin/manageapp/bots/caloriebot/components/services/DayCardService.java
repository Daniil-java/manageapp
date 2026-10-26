package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.utils.HtmlPdfRenderer;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportHtml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.function.Function;

/**
 * Карточка «мой день» — картинка для сторис и чатов: калории и БЖУ за день против нормы, серия, число блюд,
 * внизу — реферальная ссылка автора (кто придёт по ней, получит бонус, автор — награды за приглашение).
 * HTML-шаблон templates/daycard.html → PNG 1080×1350 (HtmlPdfRenderer).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DayCardService {

    static final String TEMPLATE_PATH = "templates/daycard.html";
    static final String MASCOT_PATH = "static/calorie/landing/zefirka.svg";
    private static final String IMAGES_BASE_URL = "/static/images";
    // Вариант эмодзи после символа — у монохромного шрифта его нет, остался бы пустой квадрат
    private static final String EMOJI_VARIATION = "️";

    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final StreakService streakService;
    private final CalorieTexts texts;
    // Шаблон и маскот не меняются, пока приложение работает
    private final Map<String, String> resources = new ConcurrentHashMap<>();

    public enum Status {
        /** Карточка готова. */
        OK,
        /** За сегодня нет блюд — показывать нечего. */
        EMPTY,
        /** Не удалось отрисовать. */
        FAILED
    }

    /** @param png картинка, только при OK */
    public record DayCard(Status status, byte[] png) {
    }

    /** Сумма дня и нормы: что рисуется на карточке. */
    record DayTotals(int calories, int proteins, int fats, int carbs, int dishes,
                     Integer caloriesNorm, Integer proteinsNorm, Integer fatsNorm, Integer carbsNorm) {
    }

    /**
     * Карточка за день date (календарный день пользователя): сегодня или прошлый — из истории или старого сообщения.
     * Серия — на конец этого дня.
     *
     * @param referralUrl реферальная ссылка автора — печатается внизу без https://
     * @param bonusDays   сколько дней Premium получит пришедший по ссылке
     */
    public DayCard buildCard(Long appUserId, LocalDate date, String referralUrl, int bonusDays) {
        List<Dish> dishes = dishService.getDishesForDate(appUserId, date);
        if (dishes.isEmpty()) {
            return new DayCard(Status.EMPTY, null);
        }
        DayTotals totals = getTotals(dishes, userNutritionProfileService.getOrCreateProfile(appUserId));
        String html = fillTemplateOrNull(appUserId, totals, date, streakService.getStreakEndingAt(appUserId, date),
                referralUrl, bonusDays);
        byte[] png = html == null ? null : HtmlPdfRenderer.renderPngOrNull(html, getClass().getResource(IMAGES_BASE_URL).toString());
        return png == null ? new DayCard(Status.FAILED, null) : new DayCard(Status.OK, png);
    }

    static DayTotals getTotals(List<Dish> dishes, UserNutritionProfile profile) {
        return new DayTotals(
                sum(dishes, Dish::getCalories), sum(dishes, Dish::getProteins),
                sum(dishes, Dish::getFats), sum(dishes, Dish::getCarbohydrates), dishes.size(),
                profile.getCaloriesNormPerDay(), profile.getProteinsNormGramsPerDay(),
                profile.getFatsNormGramsPerDay(), profile.getCarbsNormGramsPerDay());
    }

    /** Сумма поля по блюдам; незаполненное поле — 0. */
    private static int sum(List<Dish> dishes, Function<Dish, Integer> value) {
        return dishes.stream().map(value).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    String fillTemplateOrNull(Long appUserId, DayTotals totals, LocalDate date, int streak,
                              String referralUrl, int bonusDays) {
        String template = getCachedResourceOrNull(TEMPLATE_PATH);
        String mascot = getCachedResourceOrNull(MASCOT_PATH);
        if (template == null || mascot == null) {
            return null;
        }
        AppLanguage language = texts.languageOf(appUserId);
        String kcalUnit = texts.get(language, BotTextKeys.COMMON_UNIT_KCAL);
        String gram = texts.get(language, BotTextKeys.COMMON_UNIT_GRAM);
        boolean hasNorm = totals.caloriesNorm() != null && totals.caloriesNorm() > 0;

        String macros = buildMacroRow(texts.get(language, BotTextKeys.MACRO_LABEL_PROTEINS), totals.proteins(), totals.proteinsNorm(), gram, "p")
                + buildMacroRow(texts.get(language, BotTextKeys.MACRO_LABEL_FATS), totals.fats(), totals.fatsNorm(), gram, "f")
                + buildMacroRow(texts.get(language, BotTextKeys.MACRO_LABEL_CARBS), totals.carbs(), totals.carbsNorm(), gram, "c");

        return template
                .replace("{{LANG}}", language.getCode())
                .replace("{{TITLE}}", esc(texts.get(language, BotTextKeys.CARD_TITLE)))
                .replace("{{DATE}}", esc(DateTimeFormatter.ofPattern("d MMMM", language.getLocale()).format(date)))
                .replace("{{MASCOT}}", mascot.replace("<svg ", "<svg width=\"120\" height=\"113\" "))
                .replace("{{KCAL}}", String.valueOf(totals.calories()))
                .replace("{{KCAL_UNIT}}", esc(kcalUnit))
                .replace("{{NORM}}", esc(hasNorm
                        ? texts.get(language, BotTextKeys.CARD_OF_NORM, totals.caloriesNorm())
                        : texts.get(language, BotTextKeys.CARD_NO_NORM)))
                .replace("{{KCAL_BAR}}", hasNorm
                        ? "<div class=\"track\"><div class=\"fill\" style=\"width: "
                        + getBarPercent(totals.calories(), totals.caloriesNorm()) + "%\"></div></div>"
                        : "")
                .replace("{{MACROS}}", macros)
                .replace("{{STREAK}}", esc(texts.get(language, BotTextKeys.CARD_STREAK, streak)))
                .replace("{{DISHES}}", esc(texts.get(language, BotTextKeys.CARD_DISHES, totals.dishes())))
                .replace("{{FOOTER}}", esc(texts.get(language, BotTextKeys.CARD_FOOTER)))
                .replace("{{LINK}}", esc(referralUrl.replace("https://", "")))
                .replace("{{BONUS}}", bonusDays > 0 ? esc(texts.get(language, BotTextKeys.CARD_FOOTER_BONUS, bonusDays)) : "")
                .replace(EMOJI_VARIATION, "");
    }

    /** Строка БЖУ: подпись, «45 / 90 г» (без нормы — «45 г»), полоска. */
    private static String buildMacroRow(String label, int value, Integer norm, String unit, String cssClass) {
        boolean hasNorm = norm != null && norm > 0;
        String valueText = hasNorm ? value + " / " + norm + " " + unit : value + " " + unit;
        String bar = hasNorm
                ? "<div class=\"macro-track\"><div class=\"macro-fill " + cssClass + "\" style=\"width: "
                + getBarPercent(value, norm) + "%\"></div></div>"
                : "";
        return "<tr><td class=\"macro-label\">" + esc(label) + "</td><td class=\"macro-value\">" + esc(valueText) + "</td></tr>"
                + "<tr><td colspan=\"2\">" + bar + "</td></tr>"
                + "<tr><td colspan=\"2\" class=\"macro-gap\"></td></tr>";
    }

    /** Заполнение полоски: доля от нормы, 2–100% (перебор — полная полоска, ноль — заметная точка). */
    static int getBarPercent(int value, int norm) {
        if (norm <= 0) {
            return 0;
        }
        return (int) Math.max(2, Math.min(100, Math.round(value * 100.0 / norm)));
    }

    private static String esc(String text) {
        return ReportHtml.escapeHtml(text);
    }

    private String getCachedResourceOrNull(String path) {
        String cached = resources.get(path);
        if (cached != null) {
            return cached;
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.error("Day card resource not found: {}", path);
                return null;
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            resources.put(path, content);
            return content;
        } catch (IOException e) {
            log.error("Day card resource read failed: {}", path, e);
            return null;
        }
    }
}
