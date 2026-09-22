package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.WaterEntry;
import com.kuklin.manageapp.bots.caloriebot.components.repository.WaterEntryRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WaterEntryDto;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WeightEntryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

//Сервис для записи потребления воды
@Service
@RequiredArgsConstructor
@Slf4j
public class WaterEntryService {
    private final UserSettingsService userSettingsService;
    private final WaterEntryRepository waterEntryRepository;

    @Transactional
    public WaterEntryDto addWaterDto(Long userId, Integer amountMl) {
        return WaterEntryDto.fromEntity(addWater(userId, amountMl));
    }
    // Добавить воду (например, +250мл)
    @Transactional
    public WaterEntry addWater(Long userId, Integer amountMl) {
        //Получаем зону пользователя
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();

        log.info("WATER ADD: {}", amountMl);
        WaterEntry entry = new WaterEntry()
                .setUserId(userId)
                .setEntryDate(LocalDate.now(userZone))
                .setAmount(amountMl);
        return waterEntryRepository.save(entry);
    }

    // Получить общее количество воды за сегодня
    public Integer getTodayTotal(Long userId) {
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();

        return waterEntryRepository.findAllByUserIdAndEntryDate(userId, LocalDate.now(userZone))
                .stream()
                .mapToInt(WaterEntry::getAmount)
                .sum();
    }

    // Удалить последнюю запись (кнопка "Отмена")
    @Transactional
    public void removeLastEntry(Long userId) {
        waterEntryRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .ifPresent(waterEntryRepository::delete);
    }

    public List<WaterEntryDto> getAllWaterEntryByPeriod(Long tgUserId, LocalDate from, LocalDate to) {
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

        List<WaterEntry> weightEntryDtos = waterEntryRepository.findAllByUserIdAndCreatedAtBetween(
                tgUserId,
                fromZdt.toInstant(),
                toZdt.toInstant()
        );

        return WaterEntryDto.fromEntities(weightEntryDtos);
    }
}
