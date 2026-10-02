package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.components.repository.CalorieAiInsightRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.CalorieAiInsight;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;
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
 * Последний сгенерированный инсайт хранится в БД и отдаётся сразу,
 * ИИ вызывается только по явному запросу на обновление.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieAiInsightService {

    // Не чаще одного обновления инсайта одного типа в минуту
    private static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(1);

    private final CalorieAiInsightRepository calorieAiInsightRepository;
    private final ReportService reportService;
    private final UserSettingsService userSettingsService;
    private final ObjectMapper objectMapper;

    // Генерации, которые идут прямо сейчас: "userId:type" — защита от двойного нажатия
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    /**
     * Последний инсайт нужного типа. Без вызова ИИ.
     */
    public Optional<AiInsightDto> getLatest(Long userId, AiInsightType type) {
        ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
        return calorieAiInsightRepository.findFirstByAppUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .map(insight -> toDto(insight, zoneId));
    }

    /**
     * Последние инсайты всех типов разом — для страницы Insights.
     * Типы, которые ещё ни разу не генерировались, в ответ не попадают.
     */
    public Map<AiInsightType, AiInsightDto> getLatestAll(Long userId) {
        ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
        Map<AiInsightType, AiInsightDto> result = new EnumMap<>(AiInsightType.class);
        for (AiInsightType type : AiInsightType.values()) {
            calorieAiInsightRepository.findFirstByAppUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                    .ifPresent(insight -> result.put(type, toDto(insight, zoneId)));
        }
        return result;
    }

    /**
     * Генерирует инсайт заново через ИИ, сохраняет и возвращает.
     */
    public AiInsightDto refresh(Long userId, AiInsightType type) {
        String key = userId + ":" + type;
        if (!inProgress.add(key)) {
            throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_TOO_FREQUENT);
        }

        try {
            checkCooldown(userId, type);

            ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
            LocalDate periodTo = LocalDate.now(zoneId);
            LocalDate periodFrom = periodTo.minusDays(type.getPeriodDays() - 1L);

            Instant from = periodFrom.atStartOfDay(zoneId).toInstant();
            Instant to = periodTo.plusDays(1).atStartOfDay(zoneId).toInstant().minusMillis(1);

            String payload = reportService.getAiInsightReport(type, from, to, userId).getOrThrow();
            if (payload == null) {
                throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_FAILED);
            }

            CalorieAiInsight saved = calorieAiInsightRepository.save(new CalorieAiInsight()
                    .setAppUserId(userId)
                    .setType(type)
                    .setPeriodFrom(periodFrom)
                    .setPeriodTo(periodTo)
                    .setPayload(payload));

            return toDto(saved, zoneId);
        } catch (MissingFeatureException e) {
            throw new ErrorResponseException(ErrorStatus.MISSING_FEATURE, e);
        } finally {
            inProgress.remove(key);
        }
    }

    // --- Private Methods ---

    private void checkCooldown(Long userId, AiInsightType type) {
        calorieAiInsightRepository.findFirstByAppUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .filter(last -> last.getCreatedAt().isAfter(Instant.now().minus(REFRESH_COOLDOWN)))
                .ifPresent(last -> {
                    throw new ErrorResponseException(ErrorStatus.AI_INSIGHT_TOO_FREQUENT);
                });
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
