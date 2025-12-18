package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.repository.WeightEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeightEntryService {
    private final WeightEntryRepository weightEntryRepository;

    // Сохранить новый вес
    @Transactional
    public void updateWeight(Long userId, BigDecimal weightKg) {
        // 1. Сохраняем в историю (лог)
        WeightEntry entry = new WeightEntry()
                .setUserId(userId)
                .setEntryDate(LocalDate.now())
                .setWeightKg(weightKg);
        weightEntryRepository.save(entry);
    }

    // Получить историю веса для графика
    public List<WeightEntry> getWeightHistory(Long userId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                userId, startDate, LocalDate.now()
        );
    }

    public List<WeightEntry> getAllWeightHistory(Long userId) {
        return weightEntryRepository.findAllByUserIdOrderByEntryDateAsc(userId);
    }

    // Получить разницу (дельту) веса за период
    public BigDecimal getWeightChange(Long userId, int days) {
        List<WeightEntry> history = getWeightHistory(userId, days);
        if (history.size() < 2) return BigDecimal.ZERO;

        BigDecimal first = history.get(0).getWeightKg();
        BigDecimal last = history.get(history.size() - 1).getWeightKg();
        return last.subtract(first);
    }
}
