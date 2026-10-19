package com.kuklin.manageapp.bots.caloriebot.entities.utm;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Откуда пришёл пользователь: UTM-ссылка, по которой он впервые запустил бота. Одна строка на пользователя,
 * пишется один раз (UtmAttributionRepository.insertIfAbsent).
 */
@Entity
@Table(name = "utm_attributions")
@Data
@Accessors(chain = true)
public class UtmAttribution {
    @Id
    private Long appUserId;

    private Long utmLinkId;

    // Кто пригласил — владелец реферальной ссылки. null — пришёл не по реферальной
    private Long referrerAppUserId;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}
