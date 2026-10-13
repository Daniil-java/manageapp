package com.kuklin.manageapp.bots.caloriebot.entities.utm;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "utm_links")
@Data
@Accessors(chain = true)
public class UtmLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sourceUrl;
    private String tittle;
    @Column(unique = true, nullable = false)
    private String code;

    // Кто владелец/бенефициар (ваш ЕНАМ)
    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;

    // Кто из админов/системы создал запись
    private Long creatorId;

    @CreationTimestamp
    private Instant createdAt;

    private String description;

    @Getter
    @RequiredArgsConstructor
    public enum OwnerType {
        USER("Пользователь"), TG_CHANNEL("ТГ-Канал"), WEB("Веб");
        private final String type;
    }
}
