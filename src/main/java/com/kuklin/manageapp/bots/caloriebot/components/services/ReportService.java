package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.*;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.components.RequiresFeature;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.AiPatternAnalysisResponse;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.InsightPayloadRecord;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.NutritionAnalysisPayloadRecord;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportContext;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportResponseRecord;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    // Язык ответов ИИ для инсайтов (интерфейс сайта/миниаппки на английском)
    private static final String INSIGHT_LANGUAGE = "English";
    // Минимум разных дней с записями еды, чтобы инсайт имел смысл
    private static final int INSIGHT_MIN_DAYS_WITH_DISHES = 3;

    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final UserNutritionProfileEntryService userNutritionProfileEntryService;
    private final UserSettingsService userSettingsService;
    private final WeightEntryService weightEntryService;
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final TelegramCaloriesBotKeyComponents components;
    // Маппер с поддержкой Java 8 Time API (даты блюд в JSON для ИИ)
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Все данные пользователя за период, загруженные одним заходом.
     * Передаётся во все шаги построения отчёта, чтобы не ходить в БД повторно.
     */
    private record ReportData(
            Long userId,
            Instant from,
            Instant to,
            List<Dish> dishes,
            List<UserNutritionProfileEntry> targets,
            List<WeightEntry> weights,
            UserNutritionProfile profile,
            UserSettings settings
    ) {
        ZoneId zoneId() {
            return settings.getZoneId();
        }
    }

    // --- ПУБЛИЧНОЕ API ---

    /**
     * Формирует глубокий недельный PDF-отчет с детализацией по каждому блюду.
     */
    @RequiresFeature(value = BotFeature.REPORT_PDF_WEEK, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<byte[]> buildWeeklyDeepPdfReportOrNull(Instant from, Instant to, Long userId) {
        try {
            String template = loadTemplateOrNull(WEEKLY_REPORT_TEMPLATE_PATH);
            if (template == null) return AccessResult.success(null);

            ReportData data = loadReportData(userId, from, to);
            String aiAnalysis = getWeeklyDeepReport(data);

            Table dishesTable = ReportUtils.buildDetailedDishTable(data.dishes(), data.targets(), data.zoneId());
            String dishesTableHtml = ReportUtils.tableToHtml(dishesTable);

            // Генерируем оба графика для глубокого отчета
            String categoryChartHtml = ReportUtils.buildCategoryBarChartHtml(data.dishes());
            String timingChartHtml = ReportUtils.buildHourlyCaloriesChartHtml(data.dishes(), data.zoneId());

            String html = template
                    .replace("{{AI_ANALYSIS}}", escapeHtml(aiAnalysis))
                    .replace("{{DISHES_TABLE}}", dishesTableHtml)
                    .replace("{{CATEGORY_CHART}}", categoryChartHtml)
                    .replace("{{TIMING_CHART}}", timingChartHtml);

            return AccessResult.success(renderPdfOrNull(html));
        } catch (Exception e) {
            log.error("Failed to build Weekly Deep PDF for user {}", userId, e);
            return AccessResult.success(null);
        }
    }

    /**
     * Генерирует ИИ-инсайт для страницы Insights (саммари или шаблоны поведения за период).
     * Возвращает JSON-ответ ИИ как есть (структура зависит от типа) или null, если ИИ ответил с ошибкой.
     *
     * @throws ErrorResponseException AI_INSIGHT_NOT_ENOUGH_DATA — если еду записывали меньше чем в
     *                                {@value #INSIGHT_MIN_DAYS_WITH_DISHES} разных днях. ИИ в этом случае не вызывается.
     */
    public AccessResult<String> getAiInsightReport(AiInsightType type, Instant from, Instant to, Long userId) {
        ReportData data = loadReportData(userId, from, to);

        long daysWithDishes = data.dishes().stream()
                .filter(d -> d.getCreated() != null)
                .map(d -> d.getCreated().atZone(data.zoneId()).toLocalDate())
                .distinct()
                .count();
        if (daysWithDishes < INSIGHT_MIN_DAYS_WITH_DISHES) {
            throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_NOT_ENOUGH_DATA);
        }

        try {
            var payload = InsightPayloadRecord.of(data.profile(), data.dishes(), data.weights(), data.zoneId());
            String jsonContext = mapper.writeValueAsString(payload);

            String request = switch (type) {
                case WEEKLY_SUMMARY, MONTHLY_SUMMARY -> AI_REQUEST_INSIGHT_SUMMARY;
                case PATTERNS_WEEK, PATTERNS_MONTH -> AI_REQUEST_INSIGHT_PATTERNS;
            };
            String periodLabel = "последние " + type.getPeriodDays() + " дней";

            String prompt = AI_PERSONA + String.format(request, periodLabel, INSIGHT_LANGUAGE, jsonContext);
            String response = openAiProviderProcessor.fetchJsonResponse(
                    components.getAiKey(),
                    prompt,
                    BotIdentifier.CALORIE_BOT,
                    "AI INSIGHT " + type,
                    MetricsAiInteractionRecord.AiMessageType.TEXT
            );

            // JSON-режим гарантирует JSON, но проверяем — в jsonb всё равно ляжет только валидный
            mapper.readTree(response);
            return AccessResult.success(response);
        } catch (Exception e) {
            log.error("Failed to build AI insight {} for user {}", type, userId, e);
            return AccessResult.success(null);
        }
    }

    /**
     * Получает текстовый анализ за неделю от ИИ.
     */
    private String getWeeklyDeepReport(ReportData data) {
        String prompt = AI_PERSONA + String.format(WEEK_AI_REQUEST, data.dishes().toString(), data.profile().toString());
        return fetchAiResponse(prompt, "WEEKLY DEEP REPORT");
    }

    /**
     * Формирует текстовый ИИ-анализ питания пользователя за текущие сутки.
     */
    @RequiresFeature(value = BotFeature.REPORT_DAY, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<String> getDayAiReport(Long userId) {
        List<Dish> dishes = dishService.getTodayDishes(userId);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
        UserSettings userSettings = userSettingsService.getOrCreate(userId);
        ZonedDateTime userTime = ZonedDateTime.now(ZoneId.of(userSettings.getTimezoneId()));

        // Компоновка промпта: Личность ИИ + Инструкция + Данные профиля + Список блюд
        String prompt = AI_PERSONA + String.format(
                AI_REQUEST_TODAY_ANALYSIS,
                profile, Dish.toStringList(dishes), userTime.toString()
        );

        return AccessResult.success(fetchAiResponse(prompt, "AI DAY REPORT"));
    }

    /**
     * Точка входа для генерации комплексного PDF отчета.
     * Координирует сбор данных, работу ИИ и финальный рендеринг.
     */
    @RequiresFeature(value = BotFeature.REPORT_PDF_MONTH, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<byte[]> buildPdfReportOrNull(Instant from, Instant to, Long userId) {
        try {
            // 1. Сбор контекста (все данные и ответы ИИ в одном объекте)
            ReportContext context = gatherReportContext(loadReportData(userId, from, to));
            // 2. Заполнение HTML-шаблона данными
            String filledHtml = fillTemplateOrNull(context);
            if (filledHtml == null) return AccessResult.success(null);
            // 3. Конвертация HTML в PDF байты
            return AccessResult.success(renderPdfOrNull(filledHtml));
        } catch (Exception e) {
            log.error("Failed to build PDF report for user {}", userId, e);
            return AccessResult.success(null);
        }
    }

    // --- ЛОГИКА СБОРА ДАННЫХ (GATHERING) ---

    /**
     * Загружает из БД все данные пользователя за период — один раз на весь отчёт.
     */
    private ReportData loadReportData(Long userId, Instant from, Instant to) {
        return new ReportData(
                userId, from, to,
                dishService.getAllDishedByUserIdAndPeriod(userId, from, to),
                userNutritionProfileEntryService.getAllByUserId(userId),
                weightEntryService.getAllWeightHistory(userId),
                userNutritionProfileService.getOrCreateProfile(userId),
                userSettingsService.getOrCreate(userId)
        );
    }

    /**
     * Агрегирует данные и ответы ИИ для подготовки отчета.
     */
    private ReportContext gatherReportContext(ReportData data) throws JsonProcessingException {
        ReportResponseRecord periodAnalysis = getPeriodReport(data);
        AiPatternAnalysisResponse patternAnalysis = getNutritionReportByPeriod(data);

        Table weeklyTable = ReportUtils.buildPeriodReportPerWeek(
                data.dishes(), data.weights(), data.settings(), data.from(), data.to()
        );
        Table weightTable = ReportUtils.buildWeightHistoryTable(
                data.weights(), data.from(), data.to(), data.zoneId()
        );

        // Генерируем HTML диаграммы
        String categoryChartHtml = ReportUtils.buildCategoryBarChartHtml(data.dishes());
        String timingChartHtml = ReportUtils.buildHourlyCaloriesChartHtml(data.dishes(), data.zoneId());

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
    private AiPatternAnalysisResponse getNutritionReportByPeriod(ReportData data) throws JsonProcessingException {
        // Подготовка объекта-пейлоада для сериализации в JSON
        var payload = new NutritionAnalysisPayloadRecord(
                data.dishes(),
                data.profile(),
                data.settings().getTimezoneId()
        );

        String jsonContext = mapper.writeValueAsString(payload);
        String response = fetchAiResponse(String.format(AI_REQUEST_PATTERN_ANALYSIS, jsonContext), "Nutrition Report");

        // Маппинг JSON-ответа от ИИ обратно в Java-объект
        return mapper.readValue(response, AiPatternAnalysisResponse.class);
    }

    /**
     * Формирует отчет за период на основе ежедневных записей.
     * Отправляет таблицу данных в ИИ для получения текстовых выводов.
     */
    private ReportResponseRecord getPeriodReport(ReportData data) {
        // Построение таблицы "День | Вес | Калории | Дефицит"
        Table dailyTable = ReportUtils.buildPeriodReportPerDay(
                data.dishes(), data.targets(), data.weights(), data.settings(), data.from(), data.to()
        );

        // Анализ текстового представления таблицы через ИИ
        String aiResponse = fetchAiResponse(String.format(AI_REQUEST_PREDICTION, dailyTable.print()), "PERIOD REPORT");

        return new ReportResponseRecord(dailyTable, aiResponse, "");
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
}