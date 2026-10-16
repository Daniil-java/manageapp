package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.bots.caloriebot.entities.WaterEntry;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Accessors(chain = true)
public class WaterEntryDto {

    private Long id;
    private Long userId;
    private LocalDate entryDate;
    private Integer amount;
    private Instant createdAt;

    // ===================== ENTITY -> DTO =====================

    public static WaterEntryDto fromEntity(WaterEntry e) {
        if (e == null) return null;

        return new WaterEntryDto()
                .setId(e.getId())
                .setUserId(e.getUserId())
                .setEntryDate(e.getEntryDate())
                .setAmount(e.getAmount())
                .setCreatedAt(e.getCreatedAt());
    }

    public static List<WaterEntryDto> fromEntities(List<WaterEntry> list) {
        if (list == null || list.isEmpty()) return List.of();

        return list.stream()
                .map(WaterEntryDto::fromEntity)
                .toList();
    }

    // ===================== DTO -> ENTITY (CREATE) =====================

    public WaterEntry toEntity(Long userId) {
        return new WaterEntry()
                .setUserId(userId)
                .setEntryDate(entryDate)
                .setAmount(amount);
    }

    public static List<WaterEntry> toEntities(List<WaterEntryDto> list, Long userId) {
        if (list == null || list.isEmpty()) return List.of();

        return list.stream()
                .map(dto -> dto.toEntity(userId))
                .toList();
    }

    // ===================== MERGE =====================

    public WaterEntry mergeToEntity(WaterEntry e) {
        if (e == null) {
            e = new WaterEntry();
        }

        if (entryDate != null) {
            e.setEntryDate(entryDate);
        }

        if (amount != null) {
            e.setAmount(amount);
        }

        return e;
    }

    // ===================== FULL REPLACE =====================

    public WaterEntry toEntity(WaterEntry existing) {
        WaterEntry e = (existing != null) ? existing : new WaterEntry();

        e.setEntryDate(entryDate);
        e.setAmount(amount);

        return e;
    }
}
