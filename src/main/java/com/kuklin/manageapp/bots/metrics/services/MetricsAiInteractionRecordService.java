package com.kuklin.manageapp.bots.metrics.services;

import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.bots.metrics.repositories.MetricsAiInteractionRecordRepository;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
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
        MetricsAiInteractionRecord record = metricsAiInteractionRecordRepository.save(
                new MetricsAiInteractionRecord()
                        .setProviderVariant(providerVariant)
                        .setBotIdentifier(botIdentifier)
                        .setRequest(request)
                        .setRequestMessageType(requestMessageType)
                        .setResponse(response)
                        .setResponseMessageType(responseMessageType)
                        .setInputTokens(inputTokens != null ? inputTokens : 0L)
                        .setOutputTokens(outputTokens != null ? outputTokens : 0L)
        );

        log.info(BotIdentifier.METRICS + ": AI interaction saved! " +
                "ID {}, " +
                "Provider : {}, " +
                "BotId {}, " +
                "InputTokens {}, " +
                "OutputTokens {}",
                record.getId(),
                providerVariant,
                botIdentifier,
                inputTokens,
                outputTokens
        );
    }

    public String buildStatisticsForToday() {
        ZonedDateTime now = ZonedDateTime.now(HO_CHI_MINH_ZONE);
        ZonedDateTime startOfDay = now.toLocalDate().atStartOfDay(HO_CHI_MINH_ZONE);
        ZonedDateTime startOfNextDay = startOfDay.plusDays(1);

        Instant from = startOfDay.toInstant();
        Instant to = startOfNextDay.toInstant();

        List<MetricsAiInteractionRecord> records =
                metricsAiInteractionRecordRepository.findByCreatedBetween(from, to);

        if (records.isEmpty()) {
            return "No AI interaction metrics for today.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("COUNT, INP, OUT, TOTAL\n\n");

        records.stream()
                .collect(Collectors.groupingBy(MetricsAiInteractionRecord::getBotIdentifier))
                .forEach((botIdentifier, botRecords) -> {

                    sb.append(botIdentifier).append(":\n");

                    botRecords.stream()
                            .collect(Collectors.groupingBy(MetricsAiInteractionRecord::getProviderVariant))
                            .forEach((provider, providerRecords) -> {

                                long count = providerRecords.size();
                                long input = providerRecords.stream()
                                        .mapToLong(r -> r.getInputTokens() != null ? r.getInputTokens() : 0L)
                                        .sum();
                                long output = providerRecords.stream()
                                        .mapToLong(r -> r.getOutputTokens() != null ? r.getOutputTokens() : 0L)
                                        .sum();

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
                                        .append("\n");
                            });

                    sb.append("\n");
                });

        return sb.toString().trim();
    }


}
