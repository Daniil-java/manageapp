package com.kuklin.manageapp.bots.kworkparser.entities;

import com.kuklin.manageapp.bots.kworkparser.models.KworkDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "kworks")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Kwork {
    @Id
    private Long kworkId;
    @Enumerated(EnumType.STRING)
    private Status status;
    @UpdateTimestamp
    private LocalDateTime updated;
    @CreationTimestamp
    private LocalDateTime created;

    private String kworkStatus;
    private String name;
    private String description;
    private String categoryId;
    private String lang;
    private String priceLimit;
    private String maxDays;
    private String dateCreate;
    private String dateActive;
    private String dateExpire;
    private Long kworkUserId;
    private String username;
    private boolean isFileExist;
    private Integer kworkCount;
//    private String briefDescription;

    @Override
    public String toString() {
        return new StringBuilder()
                .append("Kwork #").append(kworkId).append("     Цена: ").append(priceLimit != null ? priceLimit : "—").append("\n")
                .append("Название: ").append(name != null ? name : "—").append("\n\n")
                .append(description).append("\n")
                .append("Срок: ").append(maxDays != null ? maxDays + " дн." : "—").append("\n")
                .append("Файлы: ").append(isFileExist ? "есть" : "нет").append("\n")
                .append("Создано: ").append(dateCreate != null ? dateCreate : "—").append("\n")
                .append("Активно с: ").append(dateActive != null ? dateActive : "—").append("\n")
                .append("Истекает: ").append(dateExpire != null ? dateExpire : "—").append("\n")
                .append("https://kwork.ru/projects/").append(kworkId)
                .toString();
    }

    public static Kwork toEntity(KworkDto dto) {
        return new Kwork()
                .setKworkId(dto.getId())
                .setStatus(Status.NEW)
                 .setName(dto.getName())
                 .setDescription(dto.getDescription())
                 .setCategoryId(dto.getCategoryId())
                 .setLang(dto.getLang())
                 .setPriceLimit(dto.getPriceLimit())
                 .setMaxDays(dto.getMaxDays())
                 .setDateCreate(dto.getDateCreate())
                 .setDateActive(dto.getDateActive())
                 .setDateExpire(dto.getDateExpire())
                 .setKworkUserId(dto.getUser() != null ? dto.getUser().getUserId() : null)
                 .setUsername(dto.getUser() != null ? dto.getUser().getUsername() : null)
                 .setFileExist(dto.getFiles() != null && !dto.getFiles().isEmpty())
                 .setKworkCount(dto.getKworkCount())
                ;

    }

    public enum Status {
        NEW, OLD, ERROR
    }

}
