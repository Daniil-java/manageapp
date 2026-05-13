package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Accessors(chain = true)
public class WeightEntryDto {
    private Long id;

    private Long userId;
    private LocalDate entryDate;
    private BigDecimal weight;
    private Instant createdAt;

    // ===================== ENTITY -> DTO =====================

    public static WeightEntryDto fromEntity(WeightEntry e) {
        if (e == null) return null;

        return new WeightEntryDto()
                .setId(e.getId())
                .setUserId(e.getUserId())
                .setEntryDate(e.getEntryDate())
                .setWeight(e.getWeight())
                .setCreatedAt(e.getCreatedAt());
    }

    public static List<WeightEntryDto> fromEntities(List<WeightEntry> list) {
        if (list == null || list.isEmpty()) return List.of();

        return list.stream()
                .map(WeightEntryDto::fromEntity)
                .toList();
    }

    // ===================== DTO -> ENTITY (CREATE) =====================

    public WeightEntry toEntity(Long userId) {
        return new WeightEntry()
                .setId(id)
                .setUserId(userId)
                .setEntryDate(entryDate)
                .setWeight(weight);
    }

    public static List<WeightEntry> toEntities(List<WeightEntryDto> list, Long userId) {
        if (list == null || list.isEmpty()) return List.of();

        return list.stream()
                .map(dto -> dto.toEntity(userId))
                .toList();
    }

    // ===================== MERGE =====================

    public WeightEntry mergeToEntity(WeightEntry e) {
        if (e == null) {
            e = new WeightEntry();
        }

        if (entryDate != null) {
            e.setEntryDate(entryDate);
        }

        if (weight != null) {
            e.setWeight(weight);
        }
        if (id != null) {
            e.setId(id);
        }

        return e;
    }

    // ===================== FULL REPLACE =====================

    public WeightEntry toEntity(WeightEntry existing) {
        WeightEntry e = (existing != null) ? existing : new WeightEntry();

        e.setEntryDate(entryDate);
        e.setWeight(weight);
        e.setId(id);

        return e;
    }
}
