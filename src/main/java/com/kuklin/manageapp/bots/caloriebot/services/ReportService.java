package com.kuklin.manageapp.bots.caloriebot.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.*;
import com.kuklin.manageapp.bots.caloriebot.models.AiPatternAnalysisResponse;
import com.kuklin.manageapp.bots.caloriebot.models.NutritionAnalysisPayloadRecord;
import com.kuklin.manageapp.bots.caloriebot.models.ReportContext;
import com.kuklin.manageapp.bots.caloriebot.models.ReportResponseRecord;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportUtils;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.tablesaw.api.Table;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

// Статические импорты для улучшения читаемости (утилиты рендеринга и ИИ-промпты)
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportCalorieBotPrompts.*;
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportUtils.escapeHtml;
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportUtils.tableToHtml;

/**
 * Сервис для формирования аналитической отчетности.
 * Объединяет данные из БД, глубокий анализ от ИИ и рендерит итоговый PDF-документ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    // --- Ресурсные константы для генерации документов ---
    private static final String REPORT_TEMPLATE_PATH = "templates/report.html";
    private static final String FONT_PATH = "/fonts/DejaVuSans.ttf";
    private static final String FONT_FAMILY = "DejaVu Sans";
    private static final String IMAGES_BASE_URL = "/static/images";
    private static final String WEEKLY_REPORT_TEMPLATE_PATH = "templates/weeklyreport.html";

    // --- Плейсхолдеры для замены данных в HTML-шаблоне ---
    private static final String VAR_AI_TEXT = "{{PERIOD_AI_TEXT}}";
    private static final String VAR_DAILY_TABLE = "{{DAILY_TABLE}}";
    private static final String VAR_WEEKLY_TABLE = "{{WEEKLY_TABLE}}";
    private static final String VAR_NUTRITION_ANALYSIS = "{{NUTRITION_ANALYSIS}}";
    private static final String VAR_WEIGHT_TABLE = "{{WEIGHT_TABLE}}";
    private static final String VAR_CATEGORY_CHART = "{{CATEGORY_CHART}}";
    private static final String VAR_TIMING_CHART = "{{TIMING_CHART}}";

    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final UserNutritionProfileEntryService userNutritionProfileEntryService;
    private final UserSettingsService userSettingsService;
    private final WeightEntryService weightEntryService;
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final TelegramCaloriesBotKeyComponents components;
    private final ObjectMapper mapper = new ObjectMapper();

    // --- ПУБЛИЧНОЕ API ---

    /**
     * Формирует глубокий недельный PDF-отчет с детализацией по каждому блюду.
     */
    public byte[] buildWeeklyDeepPdfReportOrNull(Instant from, Instant to, Long userId) {
        try {
            List<Dish> dishes = dishService.getAllDishedByUserIdAndPeriod(userId, from, to);
            List<UserNutritionProfileEntry> userNutritionEntries = userNutritionProfileEntryService.getAllByUserId(userId);
            UserSettings userSettings = userSettingsService.getOrCreate(userId);
            ZoneId zoneId = userSettings.getZoneId();

            String aiAnalysis = getWeeklyDeepReport(from, to, userId);
            String template = loadTemplateOrNull(WEEKLY_REPORT_TEMPLATE_PATH);
            if (template == null) return null;

            Table dishesTable = ReportUtils.buildDetailedDishTable(dishes, userNutritionEntries, zoneId);
            String dishesTableHtml = ReportUtils.tableToHtml(dishesTable);

            // Генерируем оба графика для глубокого отчета
            String categoryChartHtml = ReportUtils.buildCategoryBarChartHtml(dishes);
            String timingChartHtml = ReportUtils.buildHourlyCaloriesChartHtml(dishes, zoneId); // <--- Добавили

            String html = template
                    .replace("{{AI_ANALYSIS}}", escapeHtml(aiAnalysis))
                    .replace("{{DISHES_TABLE}}", dishesTableHtml)
                    .replace("{{CATEGORY_CHART}}", categoryChartHtml)
                    .replace("{{TIMING_CHART}}", timingChartHtml); // <--- Заменили плейсхолдер

            return renderPdfOrNull(html);
        } catch (Exception e) {
            log.error("Failed to build Weekly Deep PDF for user {}", userId, e);
            return null;
        }
    }

    /**
     * Получает текстовый анализ за неделю от ИИ.
     */
    public String getWeeklyDeepReport(Instant from, Instant to, Long userId) {
        List<Dish> dishes = dishService.getAllDishedByUserIdAndPeriod(userId, from, to);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);

        String prompt = AI_PERSONA + String.format(WEEK_AI_REQUEST, dishes.toString(), profile.toString());
        return fetchAiResponse(prompt, "WEEKLY DEEP REPORT");
    }

    /**
     * Формирует текстовый ИИ-анализ питания пользователя за текущие сутки.
     */
    public String getDayAiReport(Long userId) {
        List<Dish> dishes = dishService.getTodayDishes(userId);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);

        // Компоновка промпта: Личность ИИ + Инструкция + Данные профиля + Список блюд
        String prompt = AI_PERSONA + String.format(AI_REQUEST_TODAY_ANALYSIS, profile, Dish.toStringList(dishes));

        return fetchAiResponse(prompt, "AI DAY REPORT");
    }

    /**
     * Точка входа для генерации комплексного PDF отчета.
     * Координирует сбор данных, работу ИИ и финальный рендеринг.
     */
    public byte[] buildPdfReportOrNull(Instant from, Instant to, Long userId) {
        try {
            // 1. Сбор контекста (все данные и ответы ИИ в одном объекте)
            ReportContext context = gatherReportContext(from, to, userId);
            // 2. Заполнение HTML-шаблона данными
            String filledHtml = fillTemplateOrNull(context);
            if (filledHtml == null) return null;
            // 3. Конвертация HTML в PDF байты
            return renderPdfOrNull(filledHtml);
        } catch (Exception e) {
            log.error("Failed to build PDF report for user {}", userId, e);
            return null;
        }
    }

    // --- ЛОГИКА СБОРА ДАННЫХ (GATHERING) ---

    /**
     * Агрегирует данные из разных источников для подготовки отчета.
     */
    private ReportContext gatherReportContext(Instant from, Instant to, Long userId) throws JsonProcessingException {
        // Получаем блюда один раз для всех нужд
        List<Dish> dishes = dishService.getAllDishedByUserIdAndPeriod(userId, from, to);

        ReportResponseRecord periodAnalysis = getPeriodReport(from, to, userId);
        AiPatternAnalysisResponse patternAnalysis = getNutritionReportByPeriod(from, to, userId);

        // Передаем dishes в методы построения таблиц (если нужно) или используем здесь
        Table weeklyTable = ReportUtils.buildPeriodReportPerWeek(
                dishes,
                weightEntryService.getAllWeightHistory(userId),
                userSettingsService.getOrCreate(userId),
                from, to
        );

        List<WeightEntry> weightHistory = weightEntryService.getAllWeightHistory(userId);
        ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
        Table weightTable = ReportUtils.buildWeightHistoryTable(weightHistory, from, to, zoneId);

        // Генерируем HTML диаграммы
        String categoryChartHtml = ReportUtils.buildCategoryBarChartHtml(dishes);
        String timingChartHtml = ReportUtils.buildHourlyCaloriesChartHtml(dishes, zoneId);

        // Предполагаем, что вы добавили поле categoryChart в ваш record ReportContext
        return new ReportContext(
                periodAnalysis.text(),
                periodAnalysis.table(),
                weeklyTable,
                weightTable,
                patternAnalysis,
                categoryChartHtml,
                timingChartHtml
        );
    }

    /**
     * Запрашивает у ИИ детальный разбор пищевых привычек.
     * Отправляет данные в формате JSON для точного анализа.
     */
    private AiPatternAnalysisResponse getNutritionReportByPeriod(Instant from, Instant to, Long userId) throws JsonProcessingException {
        // Подготовка объекта-пейлоада для сериализации в JSON
        var payload = new NutritionAnalysisPayloadRecord(
                dishService.getAllDishedByUserIdAndPeriod(userId, from, to),
                userNutritionProfileService.getOrCreateProfile(userId),
                userSettingsService.getOrCreate(userId).getTimezoneId()
        );

        String jsonContext = getConfiguredMapper().writeValueAsString(payload);
        String response = fetchAiResponse(String.format(AI_REQUEST_PATTERN_ANALYSIS, jsonContext), "Nutrition Report");

        // Маппинг JSON-ответа от ИИ обратно в Java-объект
        return mapper.readValue(response, AiPatternAnalysisResponse.class);
    }

    /**
     * Формирует отчет за период на основе ежедневных записей.
     * Отправляет таблицу данных в ИИ для получения текстовых выводов.
     */
    private ReportResponseRecord getPeriodReport(Instant from, Instant to, Long userId) {
        List<Dish> dishes = dishService.getAllDishedByUserIdAndPeriod(userId, from, to);
        List<UserNutritionProfileEntry> targets = userNutritionProfileEntryService.getAllByUserId(userId);
        List<WeightEntry> weightEntries = weightEntryService.getAllWeightHistory(userId);
        UserSettings userSettings = userSettingsService.getOrCreate(userId);

        // Построение таблицы "День | Вес | Калории | Дефицит"
        Table dailyTable = ReportUtils.buildPeriodReportPerDay(dishes, targets, weightEntries, userSettings, from, to);

        // Анализ текстового представления таблицы через ИИ
        String aiResponse = fetchAiResponse(String.format(AI_REQUEST_PREDICTION, dailyTable.print()), "PERIOD REPORT");

        return new ReportResponseRecord(dailyTable, aiResponse, "");
    }

    /**
     * Создает таблицу с агрегированными данными по неделям (средние значения).
     */
    private Table buildWeeklyTable(Instant from, Instant to, Long userId) {
        return ReportUtils.buildPeriodReportPerWeek(
                dishService.getAllDishedByUserIdAndPeriod(userId, from, to),
                weightEntryService.getAllWeightHistory(userId),
                userSettingsService.getOrCreate(userId),
                from, to
        );
    }

    // --- РЕНДЕРИНГ И ШАБЛОНЫ ---

    /**
     * Наполняет HTML шаблон данными из контекста отчета.
     */
    private String fillTemplateOrNull(ReportContext ctx) {
        String html = loadTemplateOrNull(REPORT_TEMPLATE_PATH);
        if (html == null) return null;

        return html.replace(VAR_AI_TEXT, escapeHtml(ctx.aiSummary()))
                .replace(VAR_DAILY_TABLE, tableToHtml(ctx.dailyTable()))
                .replace(VAR_WEEKLY_TABLE, tableToHtml(ctx.weeklyTable()))
                .replace(VAR_WEIGHT_TABLE, tableToHtml(ctx.weightTable()))
                .replace(VAR_NUTRITION_ANALYSIS, ctx.patternAnalysis().toHtml())
                .replace(VAR_CATEGORY_CHART, ctx.categoryChartHtml())
                .replace(VAR_TIMING_CHART, ctx.timingChartHtml());
    }

    /**
     * Преобразует HTML-код в PDF файл с использованием шрифтов, поддерживающих кириллицу.
     */
    private byte[] renderPdfOrNull(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            String baseUrl = getClass().getResource(IMAGES_BASE_URL).toString();

            builder.withHtmlContent(html, baseUrl);
            // Подключение шрифта DejaVu для корректного отображения русского языка
            builder.useFont(() -> getClass().getResourceAsStream(FONT_PATH), FONT_FAMILY);
            builder.toStream(os);
            builder.run();

            return os.toByteArray();
        } catch (Exception e) {
            log.error("PDF rendering failed", e);
            return null;
        }
    }

    // --- УТИЛИТЫ ---

    /**
     * Обертка для запросов к OpenAiProviderProcessor.
     */
    private String fetchAiResponse(String prompt, String loggingContext) {
        return openAiProviderProcessor.fetchResponse(
                components.getAiKey(),
                prompt,
                BotIdentifier.CALORIE_BOT,
                loggingContext,
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );
    }

    /**
     * Загружает файл шаблона из папки ресурсов.
     */
    private String loadTemplateOrNull(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            return is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            log.error("Template loading failed: {}", path, e);
            return null;
        }
    }

    /**
     * Настраивает маппер для корректной обработки дат (Java 8 Time API).
     */
    private ObjectMapper getConfiguredMapper() {
        return mapper.registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}