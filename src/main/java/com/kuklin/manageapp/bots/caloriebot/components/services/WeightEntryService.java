package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.components.repository.WeightEntryRepository;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WeightEntryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeightEntryService {
    private final WeightEntryRepository weightEntryRepository;
    private final UserSettingsService userSettingsService;

    public WeightEntryDto updateWeightDto(Long userId, BigDecimal weightKg) {
        return WeightEntryDto.fromEntity(updateWeight(userId, weightKg));
    }
    // Сохранить новый вес
    @Transactional
    public WeightEntry updateWeight(Long userId, BigDecimal weightKg) {
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();

        // Сохраняем в историю (лог)
        WeightEntry entry = new WeightEntry()
                .setUserId(userId)
                .setEntryDate(LocalDate.now(userZone))
                .setWeight(weightKg);
        return weightEntryRepository.save(entry);
    }

    // Получить историю веса для графика
    public List<WeightEntry> getWeightHistory(Long userId, int days) {
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();

        LocalDate startDate = LocalDate.now(userZone).minusDays(days);
        return weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                userId, startDate, LocalDate.now(userZone)
        );
    }

    public List<WeightEntryDto> getAllWeightHistoryDto(Long userId) {
        return WeightEntryDto.fromEntities(getAllWeightHistory(userId));
    }

    public List<WeightEntry> getAllWeightHistory(Long userId) {
        return weightEntryRepository.findAllByUserIdOrderByEntryDateAsc(userId);
    }

    // Получить разницу (дельту) веса за период
    public BigDecimal getWeightChange(Long userId, int days) {
        List<WeightEntry> history = getWeightHistory(userId, days);
        if (history.size() < 2) return BigDecimal.ZERO;

        BigDecimal first = history.get(0).getWeight();
        BigDecimal last = history.get(history.size() - 1).getWeight();
        return last.subtract(first);
    }

    public void deleteWeightEntryById(Long tgUserId, Long weightId) {
        weightEntryRepository.deleteByIdAndUserId(weightId, tgUserId);
    }

    public List<WeightEntryDto> getAllWeightByPeriod(Long tgUserId, LocalDate from, LocalDate to) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        // Выдаст ровно "2026-04-29T00:00:00"
        String fromStr = from.atStartOfDay().format(formatter);

        // Выдаст ровно "2026-05-28T23:59:59"
        String toStr = to.atTime(23, 59, 59).format(formatter);


        ZoneId userZone = userSettingsService.getOrCreate(tgUserId).getZoneId();

        ZonedDateTime fromZdt = ZonedDateTime.of(
                LocalDateTime.parse(fromStr),
                userZone
        );

        ZonedDateTime toZdt = ZonedDateTime.of(
                LocalDateTime.parse(toStr),
                userZone
        );

        List<WeightEntry> weightEntryDtos = weightEntryRepository.findAllByUserIdAndCreatedAtBetween(
                tgUserId,
                fromZdt.toInstant(),
                toZdt.toInstant()
        );

        return WeightEntryDto.fromEntities(weightEntryDtos);

    }
}
