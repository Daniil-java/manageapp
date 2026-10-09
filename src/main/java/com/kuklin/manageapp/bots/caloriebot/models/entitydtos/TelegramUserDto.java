package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TelegramUserDto {

    private Long id;
    private Long telegramId;
    private String username;
    private String firstname;
    private String lastname;
    private String languageCode;
    private BotIdentifier botIdentifier;
    private Long responseCount;
    private Boolean isBotBlocked;
    private LocalDateTime created;
    private LocalDateTime updated;

    public TelegramUserDto toDto() {
        TelegramUserDto dto = new TelegramUserDto();
        dto.setId(this.id);
        dto.setTelegramId(this.telegramId);
        dto.setUsername(this.username);
        dto.setFirstname(this.firstname);
        dto.setLastname(this.lastname);
        dto.setLanguageCode(this.languageCode);
        dto.setBotIdentifier(this.botIdentifier);
        dto.setResponseCount(this.responseCount);
        dto.setIsBotBlocked(this.isBotBlocked);
        dto.setCreated(this.created);
        dto.setUpdated(this.updated);
        return dto;
    }

    public static TelegramUser fromDto(TelegramUserDto dto) {
        return new TelegramUser()
                .setId(dto.getId())
                .setTelegramId(dto.getTelegramId())
                .setUsername(dto.getUsername())
                .setFirstname(dto.getFirstname())
                .setLastname(dto.getLastname())
                .setLanguageCode(dto.getLanguageCode())
                .setBotIdentifier(dto.getBotIdentifier())
                .setResponseCount(dto.getResponseCount())
                .setIsBotBlocked(dto.getIsBotBlocked());
    }
}
