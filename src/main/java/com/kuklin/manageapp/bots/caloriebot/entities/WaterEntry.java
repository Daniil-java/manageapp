package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "water_entries")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class WaterEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private LocalDate entryDate;
    private Integer amountMl;
    @CreationTimestamp
    private Instant createdAt;

    public static String getWaterStatusText(Integer currentWaterMl, Integer targetWaterMl) {
        int totalBlocks = 10; // Длина шкалы

        if (targetWaterMl == null) {
            targetWaterMl = UserNutritionProfile.DEF_WATER_ML;
        }

        // Считаем количество закрашенных блоков
        int filledBlocks = (int) Math.round(((double) currentWaterMl / targetWaterMl) * totalBlocks);
        // Ограничиваем, чтобы не вышло за пределы 0-10
        filledBlocks = Math.max(0, Math.min(totalBlocks, filledBlocks));

        String filled = "🟦".repeat(filledBlocks);
        String empty = "⬜".repeat(totalBlocks - filledBlocks);

        return String.format("💧 [%s%s] %d / %d мл", filled, empty, currentWaterMl, targetWaterMl);
    }
}
