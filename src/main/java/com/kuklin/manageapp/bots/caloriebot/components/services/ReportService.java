package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.*;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.components.RequiresFeature;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiInputLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.PdfTextKeys;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.ReportAiPayloadRecord;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.ReportAiResponse;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportKind;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportSource;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats;
import com.kuklin.manageapp.bots.caloriebot.texts.PdfTexts;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.InsightPayloadRecord;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.utils.DayReportPrompt;
import com.kuklin.manageapp.bots.caloriebot.utils.GoalPromptRules;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportHtml;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportStatsCalculator;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

// Статические импорты для улучшения читаемости (ИИ-промпты)
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportCalorieBotPrompts.*;

/**
 * Сервис для формирования аналитической отчетности.
 * Считает метрики по данным из БД, просит ИИ объяснить их и рендерит итоговый PDF-документ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    // --- Ресурсы для генерации PDF ---
    private static final String REPORT_TEMPLATE_PATH = "templates/report.html";
    private static final String MASCOT_PATH = "static/calorie/landing/zefirka.svg";
    private static final String IMAGES_BASE_URL = "/static/images";
    private static final String CLASSPATH_PROTOCOL = "classpath";

    /**
     * Шрифты PDF. Nunito — как в мини-приложении (для SVG-графиков он же подключён в шаблоне через @font-face).
     * Noto Emoji (монохромные эмодзи: цветные рендерер не умеет) и DejaVu Sans (стрелки и символы,
     * которых нет в Nunito) — запасные: подставляются для недостающих символов.
     */
    private static final List<ReportFont> FONTS = List.of(
            new ReportFont("/fonts/Nunito-Regular.ttf", "Nunito", 400, false),
            new ReportFont("/fonts/Nunito-Bold.ttf", "Nunito", 700, false),
            new ReportFont("/fonts/Nunito-ExtraBold.ttf", "Nunito", 800, false),
            new ReportFont("/fonts/Nunito-Black.ttf", "Nunito", 900, false),
            new ReportFont("/fonts/NotoEmoji-Regular.ttf", "Noto Emoji", 400, true),
            new ReportFont("/fonts/DejaVuSans.ttf", "DejaVu Sans", 400, true)
    );

    // --- Плейсхолдеры данных в HTML-шаблоне ---
    private static final String VAR_TITLE = "{{TITLE}}";
    private static final String VAR_MASCOT = "{{MASCOT}}";
    private static final String VAR_FOOTER_TEXT = "{{FOOTER_TEXT}}";
    private static final String VAR_COVER = "{{COVER}}";
    private static final String VAR_EMPTY = "{{EMPTY}}";
    private static final String VAR_CALENDAR = "{{CALENDAR}}";
    private static final String VAR_CALORIES = "{{CALORIES}}";
    private static final String VAR_WEIGHT = "{{WEIGHT}}";
    private static final String VAR_ENERGY = "{{ENERGY}}";
    private static final String VAR_MACROS = "{{MACROS}}";
    private static final String VAR_RHYTHM = "{{RHYTHM}}";
    private static final String VAR_TOP = "{{TOP}}";
    private static final String VAR_PATTERNS = "{{PATTERNS}}";
    private static final String VAR_SWAPS = "{{SWAPS}}";
    private static final String VAR_CATEGORIES = "{{CATEGORIES}}";
    private static final String VAR_WATER = "{{WATER}}";
    private static final String VAR_DAILY = "{{DAILY}}";
    private static final String VAR_DISHES = "{{DISHES}}";
    private static final String VAR_QUALITY = "{{QUALITY}}";

    private static final String EMOJI_VARIATION = "️";

    // Минимум разных дней с записями еды, чтобы инсайт имел смысл
    private static final int INSIGHT_MIN_DAYS_WITH_DISHES = 3;

    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final UserSettingsService userSettingsService;
    private final WeightEntryService weightEntryService;
    private final ReportSourceLoader reportSourceLoader;
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final TelegramCaloriesBotKeyComponents components;
    private final AiInputLimitsProperties aiInputLimits;
    private final CalorieTexts calorieTexts;
    // Шаблон и маскот не меняются, пока приложение работает: читаем с диска один раз, а не на каждый PDF
    private final Map<String, String> resources = new ConcurrentHashMap<>();
    // Маппер с поддержкой Java 8 Time API (даты блюд в JSON для ИИ)
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Ресурс приложения по адресу classpath:/путь из HTML-шаблона. */
    private record ClasspathStream(String url) implements FSStream {
        @Override
        public InputStream getStream() {
            return ReportService.class.getResourceAsStream(url.substring(CLASSPATH_PROTOCOL.length() + 1));
        }

        @Override
        public Reader getReader() {
            InputStream stream = getStream();
            return stream != null ? new InputStreamReader(stream, StandardCharsets.UTF_8) : null;
        }
    }

    /** Шрифт PDF; fallback — подставляется только для символов, которых нет в основном шрифте. */
    private record ReportFont(String path, String family, int weight, boolean fallback) {
    }

    /**
     * Данные пользователя за период для ИИ-инсайтов, загруженные одним заходом.
     */
    private record InsightData(List<Dish> dishes,
                               List<WeightEntry> weights,
                               UserNutritionProfile profile,
                               ZoneId zoneId) {
    }

    // --- ПУБЛИЧНОЕ API ---

    /**
     * PDF-отчёт за последние 7 дней: обложка с главными цифрами, графики, разбор ИИ и блюда по дням.
     */
    @RequiresFeature(value = BotFeature.REPORT_PDF_WEEK, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<byte[]> buildWeeklyPdfReportOrNull(Long userId) {
        return AccessResult.success(buildPdfReportOrNull(ReportKind.WEEK, Instant.now(), userId));
    }

    /**
     * PDF-отчёт за последние 30 дней: то же, что недельный, плюс календарь соблюдения нормы.
     */
    @RequiresFeature(value = BotFeature.REPORT_PDF_MONTH, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<byte[]> buildMonthlyPdfReportOrNull(Long userId) {
        return AccessResult.success(buildPdfReportOrNull(ReportKind.MONTH, Instant.now(), userId));
    }

    /**
     * Генерирует ИИ-инсайт для страницы Insights (саммари или шаблоны поведения за период).
     * Возвращает JSON-ответ ИИ как есть (структура зависит от типа) или null, если ИИ ответил с ошибкой.
     *
     * @throws ErrorResponseException AI_INSIGHT_NOT_ENOUGH_DATA — если еду записывали меньше чем в
     *                                {@value #INSIGHT_MIN_DAYS_WITH_DISHES} разных днях. ИИ в этом случае не вызывается.
     */
    public AccessResult<String> getAiInsightReport(AiInsightType type, Instant from, Instant to, Long userId,
                                                   AppLanguage language) {
        InsightData data = loadInsightData(userId, from, to);

        long daysWithDishes = data.dishes().stream()
                .filter(d -> d.getCreated() != null)
                .map(d -> d.getCreated().atZone(data.zoneId()).toLocalDate())
                .distinct()
                .count();
        if (daysWithDishes < INSIGHT_MIN_DAYS_WITH_DISHES) {
            throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_NOT_ENOUGH_DATA);
        }

        try {
            var payload = InsightPayloadRecord.of(data.profile(), data.dishes(), data.weights(), data.zoneId(),
                    aiInputLimits.getInsightMaxDishes());
            String jsonContext = mapper.writeValueAsString(payload);

            String request = switch (type) {
                case WEEKLY_SUMMARY, MONTHLY_SUMMARY -> AI_REQUEST_INSIGHT_SUMMARY;
                case PATTERNS_WEEK, PATTERNS_MONTH -> AI_REQUEST_INSIGHT_PATTERNS;
            };
            String periodLabel = "последние " + type.getPeriodDays() + " дней";

            String prompt = AI_PERSONA + String.format(request, periodLabel, language.getNativeName(), jsonContext,
                    GoalPromptRules.buildRules(data.profile()));
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
     * Формирует текстовый ИИ-анализ питания пользователя за текущие сутки.
     */
    @RequiresFeature(value = BotFeature.REPORT_DAY, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<String> getDayAiReport(Long userId) {
        List<Dish> dishes = dishService.getTodayDishes(userId);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
        ZonedDateTime userTime = ZonedDateTime.now(userSettingsService.getZoneId(userId));

        String prompt = DayReportPrompt.buildPrompt(profile, dishes, userTime,
                calorieTexts.languageOf(userId).getNativeName(), ThreadLocalRandom.current());

        return AccessResult.success(fetchCreativeAiResponse(prompt, "AI DAY REPORT"));
    }

    // --- PDF-ОТЧЁТ ---

    /**
     * Собирает PDF за последние kind.getDays() дней по таймзоне пользователя, включая сегодняшний.
     * ИИ не ответил — отчёт всё равно собирается, без блоков ИИ. null — не получилось отрендерить.
     */
    byte[] buildPdfReportOrNull(ReportKind kind, Instant now, Long userId) {
        try {
            ReportSource source = reportSourceLoader.loadLastDays(kind, now, userId);
            ReportStats stats = ReportStatsCalculator.buildStats(source);
            PdfTexts texts = new PdfTexts(calorieTexts, calorieTexts.languageOf(userId));
            ReportAiResponse ai = stats.hasFood() ? fetchReportAiOrEmpty(stats, source, texts) : ReportAiResponse.empty();

            String html = fillTemplateOrNull(source, stats, ai, texts, now);
            return html != null ? renderPdfOrNull(html) : null;
        } catch (Exception e) {
            log.error("Failed to build {} PDF report for user {}", kind, userId, e);
            return null;
        }
    }

    /**
     * Просит ИИ объяснить посчитанные метрики: главная фраза, шаблоны поведения, замены.
     * Ошибка ИИ не ломает отчёт — блоки ИИ просто не показываются.
     */
    private ReportAiResponse fetchReportAiOrEmpty(ReportStats stats, ReportSource source, PdfTexts texts) {
        try {
            var payload = ReportAiPayloadRecord.of(stats, source.profile(), source.dishes(), source.zoneId());
            String prompt = AI_PERSONA + String.format(AI_REQUEST_REPORT,
                    source.kind().getDays(), texts.language().getNativeName(), mapper.writeValueAsString(payload),
                    GoalPromptRules.buildRules(source.profile()));
            String response = openAiProviderProcessor.fetchJsonResponse(
                    components.getAiKey(),
                    prompt,
                    BotIdentifier.CALORIE_BOT,
                    "PDF REPORT " + source.kind(),
                    MetricsAiInteractionRecord.AiMessageType.TEXT
            );
            ReportAiResponse ai = response != null ? mapper.readValue(response, ReportAiResponse.class) : null;
            return ai != null ? ai : ReportAiResponse.empty();
        } catch (Exception e) {
            log.error("AI part of {} PDF report failed", source.kind(), e);
            return ReportAiResponse.empty();
        }
    }

    /**
     * Наполняет HTML-шаблон: сначала тексты словаря {{pdf.…}}, потом данные — {{pdf.…}} в ответе ИИ подставляться не должны.
     */
    private String fillTemplateOrNull(ReportSource source, ReportStats stats, ReportAiResponse ai, PdfTexts texts, Instant now) {
        String template = getCachedResourceOrNull(REPORT_TEMPLATE_PATH);
        String mascot = getCachedResourceOrNull(MASCOT_PATH);
        if (template == null || mascot == null) return null;

        boolean month = source.kind() == ReportKind.MONTH;
        String footer = texts.get(PdfTextKeys.FOOTER,
                texts.formatDate(stats.startDate(), PdfTextKeys.DATE_DAY), texts.formatDate(stats.endDate(), PdfTextKeys.DATE));

        // U+FE0F (вариант эмодзи после ⚖️, 🗓️) — у монохромного шрифта нет такого символа, остался бы пустой квадрат
        return texts.localizeTemplate(template)
                .replace(VAR_TITLE, ReportHtml.escapeHtml(texts.get(source.kind().getTitleKey())))
                .replace(VAR_MASCOT, sizeSvg(mascot, 14, 13))
                .replace(VAR_FOOTER_TEXT, ReportHtml.escapeHtml(footer))
                .replace(VAR_COVER, ReportHtml.buildCover(stats, ai, source.profile(), texts, sizeSvg(mascot, 66, 62),
                        ZonedDateTime.ofInstant(now, source.zoneId())))
                .replace(VAR_EMPTY, ReportHtml.buildEmpty(stats, texts))
                .replace(VAR_CALENDAR, month ? ReportHtml.buildCalendar(stats, texts) : "")
                .replace(VAR_CALORIES, ReportHtml.buildCaloriesSection(stats, texts))
                .replace(VAR_WEIGHT, ReportHtml.buildWeightSection(stats, texts))
                .replace(VAR_ENERGY, ReportHtml.buildEnergySection(stats, source.profile(), texts))
                .replace(VAR_MACROS, ReportHtml.buildMacrosSection(stats, texts))
                .replace(VAR_RHYTHM, ReportHtml.buildRhythmSection(stats, texts))
                .replace(VAR_TOP, ReportHtml.buildTopSection(stats, texts))
                .replace(VAR_PATTERNS, ReportHtml.buildPatternsSection(ai, texts))
                .replace(VAR_SWAPS, ReportHtml.buildSwapsSection(ai, texts))
                .replace(VAR_CATEGORIES, ReportHtml.buildCategoriesSection(stats, texts))
                .replace(VAR_WATER, ReportHtml.buildWaterSection(stats, texts))
                .replace(VAR_DAILY, ReportHtml.buildDailySection(stats, texts))
                .replace(VAR_DISHES, month ? "" : ReportHtml.buildDishesSection(source.dishes(), source.zoneId(), texts))
                .replace(VAR_QUALITY, ReportHtml.buildQualitySection(stats, texts))
                .replace(EMOJI_VARIATION, "");
    }

    /** Размер inline-SVG маскота: у файла есть только viewBox. */
    private static String sizeSvg(String svg, int width, int height) {
        return svg.replace("<svg ", "<svg width=\"" + width + "\" height=\"" + height + "\" ");
    }

    /**
     * Рендерит XHTML в PDF: SVG-графики через Batik, шрифты из ресурсов.
     */
    private byte[] renderPdfOrNull(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useSVGDrawer(new BatikSVGDrawer());
            builder.withHtmlContent(html, getClass().getResource(IMAGES_BASE_URL).toString());
            // classpath:/fonts/… в @font-face шаблона — шрифты для текста SVG-графиков (их Batik берёт только оттуда)
            builder.useProtocolsStreamImplementation(ClasspathStream::new, CLASSPATH_PROTOCOL);
            for (ReportFont font : FONTS) {
                Set<BaseRendererBuilder.FSFontUseCase> useCases = font.fallback()
                        ? EnumSet.of(BaseRendererBuilder.FSFontUseCase.DOCUMENT, BaseRendererBuilder.FSFontUseCase.FALLBACK_FINAL)
                        : EnumSet.of(BaseRendererBuilder.FSFontUseCase.DOCUMENT);
                builder.useFont(() -> getClass().getResourceAsStream(font.path()), font.family(), font.weight(),
                        BaseRendererBuilder.FontStyle.NORMAL, true, useCases);
            }
            builder.toStream(os);
            builder.run();

            return os.toByteArray();
        } catch (Exception e) {
            log.error("PDF rendering failed", e);
            return null;
        }
    }

    // --- УТИЛИТЫ ---

    private InsightData loadInsightData(Long userId, Instant from, Instant to) {
        return new InsightData(
                dishService.getAllDishedByUserIdAndPeriod(userId, from, to),
                weightEntryService.getAllWeightHistory(userId),
                userNutritionProfileService.getOrCreateProfile(userId),
                userSettingsService.getZoneId(userId)
        );
    }

    /**
     * Обертка для запросов к OpenAiProviderProcessor.
     */
    private String fetchCreativeAiResponse(String prompt, String loggingContext) {
        return openAiProviderProcessor.fetchCreativeResponse(
                components.getAiKey(),
                prompt,
                BotIdentifier.CALORIE_BOT,
                loggingContext,
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );
    }

    /** Текстовый файл из ресурсов, прочитанный один раз. Не прочитался — null, в кэш не кладём, попробуем снова. */
    private String getCachedResourceOrNull(String path) {
        return resources.computeIfAbsent(path, this::loadResourceOrNull);
    }

    /**
     * Загружает текстовый файл из ресурсов.
     */
    private String loadResourceOrNull(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            return is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            log.error("Resource loading failed: {}", path, e);
            return null;
        }
    }
}
