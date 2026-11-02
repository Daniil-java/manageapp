package com.kuklin.manageapp.bots.metrics.services;

import com.kuklin.manageapp.aiconversation.models.AiUsageContext;
import com.kuklin.manageapp.aiconversation.models.enums.AiModelPrice;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.bots.metrics.repositories.MetricsAiInteractionRecordRepository;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsAiInteractionRecordService {
    private final MetricsAiInteractionRecordRepository metricsAiInteractionRecordRepository;
    private static final ZoneId HO_CHI_MINH_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public void saveInteractionRecord(
            ProviderVariant providerVariant,
            BotIdentifier botIdentifier,
            String request,
            MetricsAiInteractionRecord.AiMessageType requestMessageType,
            String response,
            MetricsAiInteractionRecord.AiMessageType responseMessageType,
            Long inputTokens,
            Long outputTokens
    ) {
        saveInteractionRecord(providerVariant, botIdentifier, null, request, requestMessageType,
                response, responseMessageType, inputTokens, 0L, outputTokens);
    }

    /**
     * Запись вызова ИИ со стоимостью. Пользователь и цель вызова — из {@link AiUsageContext} текущего потока,
     * стоимость — по {@link AiModelPrice} (модели нет в прайсе — cost_usd пустой).
     */
    public void saveInteractionRecord(
            ProviderVariant providerVariant,
            BotIdentifier botIdentifier,
            String model,
            String request,
            MetricsAiInteractionRecord.AiMessageType requestMessageType,
            String response,
            MetricsAiInteractionRecord.AiMessageType responseMessageType,
            Long inputTokens,
            Long cachedInputTokens,
            Long outputTokens
    ) {
        long input = inputTokens != null ? inputTokens : 0L;
        long cached = cachedInputTokens != null ? cachedInputTokens : 0L;
        long output = outputTokens != null ? outputTokens : 0L;
        BigDecimal costUsd = AiModelPrice.findByModel(model)
                .map(price -> price.getCostUsd(input, cached, output))
                .orElse(null);

        MetricsAiInteractionRecord record = metricsAiInteractionRecordRepository.save(
                new MetricsAiInteractionRecord()
                        .setProviderVariant(providerVariant)
                        .setBotIdentifier(botIdentifier)
                        .setModel(model)
                        .setAppUserId(AiUsageContext.getCurrentAppUserIdOrNull())
                        .setOperation(AiUsageContext.getCurrentOperationOrNull())
                        .setRequest(request)
                        .setRequestMessageType(requestMessageType)
                        .setResponse(response)
                        .setResponseMessageType(responseMessageType)
                        .setInputTokens(input)
                        .setCachedInputTokens(cached)
                        .setOutputTokens(output)
                        .setCostUsd(costUsd)
        );

        log.info(BotIdentifier.METRICS + ": AI interaction saved! " +
                "ID {}, " +
                "Provider : {}, " +
                "BotId {}, " +
                "Model {}, " +
                "User {}, " +
                "Operation {}, " +
                "InputTokens {}, " +
                "OutputTokens {}, " +
                "CostUsd {}",
                record.getId(),
                providerVariant,
                botIdentifier,
                model,
                record.getAppUserId(),
                record.getOperation(),
                input,
                output,
                costUsd
        );
    }

    /**
     * Запись упавшего вызова ИИ: без токенов и стоимости, с HTTP-кодом (0 — ответа не было) и текстом ошибки.
     * Сбой записи метрики не должен подменять исходную ошибку провайдера — только логируем.
     */
    public void saveFailedInteractionRecord(
            ProviderVariant providerVariant,
            BotIdentifier botIdentifier,
            String model,
            String request,
            MetricsAiInteractionRecord.AiMessageType requestMessageType,
            int errorCode,
            String error
    ) {
        try {
            MetricsAiInteractionRecord record = metricsAiInteractionRecordRepository.save(
                    new MetricsAiInteractionRecord()
                            .setProviderVariant(providerVariant)
                            .setBotIdentifier(botIdentifier)
                            .setModel(model)
                            .setAppUserId(AiUsageContext.getCurrentAppUserIdOrNull())
                            .setOperation(AiUsageContext.getCurrentOperationOrNull())
                            .setRequest(request)
                            .setRequestMessageType(requestMessageType)
                            .setErrorCode(errorCode)
                            .setError(error)
            );
            log.info(BotIdentifier.METRICS + ": failed AI interaction saved! ID {}, Provider {}, BotId {}, Model {}, "
                            + "User {}, Operation {}, ErrorCode {}",
                    record.getId(), providerVariant, botIdentifier, model,
                    record.getAppUserId(), record.getOperation(), errorCode);
        } catch (RuntimeException e) {
            log.warn(BotIdentifier.METRICS + ": failed to save failed AI interaction: {}", e.getMessage());
        }
    }

    /** Статистика вызовов ИИ за день day (по Хошимину). */
    public String buildStatistics(LocalDate day) {
        ZonedDateTime startOfDay = day.atStartOfDay(HO_CHI_MINH_ZONE);
        ZonedDateTime startOfNextDay = startOfDay.plusDays(1);

        Instant from = startOfDay.toInstant();
        Instant to = startOfNextDay.toInstant();

        List<MetricsAiInteractionRecord> records =
                metricsAiInteractionRecordRepository.findByCreatedBetween(from, to);

        if (records.isEmpty()) {
            return "No AI interaction metrics for " + day + ".";
        }

        StringBuilder sb = new StringBuilder();
        // COUNT — успешные вызовы, ERR — упавшие (без токенов и стоимости)
        sb.append("COUNT, INP, OUT, TOTAL, $, ERR\n\n");

        records.stream()
                .collect(Collectors.groupingBy(MetricsAiInteractionRecord::getBotIdentifier))
                .forEach((botIdentifier, botRecords) -> {

                    sb.append(botIdentifier).append(":\n");

                    botRecords.stream()
                            .collect(Collectors.groupingBy(MetricsAiInteractionRecord::getProviderVariant))
                            .forEach((provider, providerRecords) -> {

                                long errors = providerRecords.stream()
                                        .filter(r -> r.getErrorCode() != null)
                                        .count();
                                long count = providerRecords.size() - errors;
                                long input = providerRecords.stream()
                                        .mapToLong(r -> r.getInputTokens() != null ? r.getInputTokens() : 0L)
                                        .sum();
                                long output = providerRecords.stream()
                                        .mapToLong(r -> r.getOutputTokens() != null ? r.getOutputTokens() : 0L)
                                        .sum();
                                // Без цены (старые записи, модель не из прайса) — не входит в сумму
                                BigDecimal costUsd = providerRecords.stream()
                                        .map(MetricsAiInteractionRecord::getCostUsd)
                                        .filter(Objects::nonNull)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                        .setScale(2, RoundingMode.HALF_UP);

                                sb.append("---")
                                        .append(provider)
                                        .append(": ")
                                        .append(count)
                                        .append(", ")
                                        .append(input)
                                        .append(", ")
                                        .append(output)
                                        .append(", ")
                                        .append(input + output)
                                        .append(", ")
                                        .append(costUsd)
                                        .append(", ")
                                        .append(errors)
                                        .append("\n");
                            });

                    sb.append("\n");
                });

        return sb.toString().trim();
    }


}
