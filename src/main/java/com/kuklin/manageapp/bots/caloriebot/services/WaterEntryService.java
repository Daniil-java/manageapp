package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.bots.caloriebot.entities.WaterEntry;
import com.kuklin.manageapp.bots.caloriebot.repository.WaterEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

//Сервис для записи потребления воды
@Service
@RequiredArgsConstructor
@Slf4j
public class WaterEntryService {
    private final UserSettingsService userSettingsService;
    private final WaterEntryRepository waterEntryRepository;

    // Добавить воду (например, +250мл)
    @Transactional
    public void addWater(Long userId, Integer amountMl) {
        //Получаем зону пользователя
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();

        log.info("WATER ADD: {}", amountMl);
        WaterEntry entry = new WaterEntry()
                .setUserId(userId)
                .setEntryDate(LocalDate.now(userZone))
                .setAmountMl(amountMl);
        log.info("WATER ADD: {}", waterEntryRepository.save(entry).getAmountMl());
    }

    // Получить общее количество воды за сегодня
    public Integer getTodayTotal(Long userId) {
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();

        return waterEntryRepository.findAllByUserIdAndEntryDate(userId, LocalDate.now(userZone))
                .stream()
                .mapToInt(WaterEntry::getAmountMl)
                .sum();
    }

    // Удалить последнюю запись (кнопка "Отмена")
    @Transactional
    public void removeLastEntry(Long userId) {
        waterEntryRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .ifPresent(waterEntryRepository::delete);
    }
}
