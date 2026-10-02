package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.components.repository.CalorieAiInsightRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.CalorieAiInsight;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.report.AiInsightDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ИИ-инсайты для страницы Insights: саммари и шаблоны поведения за неделю и месяц.
 * На пользователя и тип хранится одна запись — последний сгенерированный инсайт. Он отдаётся сразу,
 * ИИ вызывается только по явному запросу на обновление.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieAiInsightService {

    // Не чаще одного обращения к ИИ на (пользователь, тип) в минуту — считаются и неудачные
    private static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(1);

    private final CalorieAiInsightRepository calorieAiInsightRepository;
    private final ReportService reportService;
    private final UserSettingsService userSettingsService;
    private final ObjectMapper objectMapper;

    // Генерации, которые идут прямо сейчас — защита от двойного нажатия. Ключ "userId:type"
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();
    // Когда последний раз обращались к ИИ (успешно или нет). Ключ "userId:type"
    private final Map<String, Instant> lastAiCalls = new ConcurrentHashMap<>();

    /**
     * Последний инсайт нужного типа. Без вызова ИИ.
     */
    public Optional<AiInsightDto> getLatest(Long userId, AiInsightType type) {
        ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
        return calorieAiInsightRepository.findByAppUserIdAndType(userId, type)
                .map(insight -> toDto(insight, zoneId));
    }

    /**
     * Последние инсайты всех типов одним запросом — для страницы Insights.
     * Типы, которые ещё ни разу не генерировались, в ответ не попадают.
     */
    public Map<AiInsightType, AiInsightDto> getLatestAll(Long userId) {
        ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
        Map<AiInsightType, AiInsightDto> result = new EnumMap<>(AiInsightType.class);
        calorieAiInsightRepository.findAllByAppUserId(userId)
                .forEach(insight -> result.put(insight.getType(), toDto(insight, zoneId)));
        return result;
    }

    /**
     * Генерирует инсайт заново через ИИ, перезаписывает сохранённый и возвращает.
     *
     * @throws ErrorResponseException AI_INSIGHT_TOO_FREQUENT — генерация уже идёт или к ИИ обращались меньше минуты назад;
     *                                AI_INSIGHT_NOT_ENOUGH_DATA — мало записей еды (ИИ не вызывается);
     *                                AI_INSIGHT_FAILED — ИИ ответил с ошибкой.
     */
    public AiInsightDto refresh(Long userId, AiInsightType type) {
        String key = userId + ":" + type;
        if (!inProgress.add(key)) {
            throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_TOO_FREQUENT);
        }

        try {
            checkCooldown(key);

            ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
            LocalDate periodTo = LocalDate.now(zoneId);
            LocalDate periodFrom = periodTo.minusDays(type.getPeriodDays() - 1L);

            Instant from = periodFrom.atStartOfDay(zoneId).toInstant();
            Instant to = periodTo.plusDays(1).atStartOfDay(zoneId).toInstant().minusMillis(1);

            // Если данных мало — бросит AI_INSIGHT_NOT_ENOUGH_DATA до вызова ИИ, cooldown не тратится
            AccessResult<String> result = reportService.getAiInsightReport(type, from, to, userId);
            lastAiCalls.put(key, Instant.now());

            String payload = result.getOrThrow();
            if (payload == null) {
                throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_FAILED);
            }

            CalorieAiInsight insight = calorieAiInsightRepository.findByAppUserIdAndType(userId, type)
                    .orElseGet(() -> new CalorieAiInsight().setAppUserId(userId).setType(type));
            insight.setPeriodFrom(periodFrom)
                    .setPeriodTo(periodTo)
                    .setPayload(payload)
                    .setCreatedAt(Instant.now());

            return toDto(calorieAiInsightRepository.save(insight), zoneId);
        } catch (MissingFeatureException e) {
            throw new ErrorResponseException(ErrorStatus.MISSING_FEATURE, e);
        } finally {
            inProgress.remove(key);
        }
    }

    // --- Private Methods ---

    private void checkCooldown(String key) {
        Instant lastCall = lastAiCalls.get(key);
        if (lastCall != null && lastCall.isAfter(Instant.now().minus(REFRESH_COOLDOWN))) {
            throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_TOO_FREQUENT);
        }
    }

    /**
     * Устарел ли инсайт: прошло staleAfterDays дней с даты генерации (в таймзоне пользователя).
     */
    private boolean isStale(CalorieAiInsight insight, ZoneId zoneId) {
        LocalDate createdDate = insight.getCreatedAt().atZone(zoneId).toLocalDate();
        return !createdDate.plusDays(insight.getType().getStaleAfterDays()).isAfter(LocalDate.now(zoneId));
    }

    private AiInsightDto toDto(CalorieAiInsight insight, ZoneId zoneId) {
        try {
            return new AiInsightDto(
                    insight.getType(),
                    insight.getPeriodFrom(),
                    insight.getPeriodTo(),
                    insight.getCreatedAt(),
                    isStale(insight, zoneId),
                    objectMapper.readTree(insight.getPayload())
            );
        } catch (JsonProcessingException e) {
            // В БД лежит только проверенный JSON, сюда попадать не должны
            log.error("Broken AI insight payload, id {}", insight.getId(), e);
            throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_FAILED, e);
        }
    }
}
