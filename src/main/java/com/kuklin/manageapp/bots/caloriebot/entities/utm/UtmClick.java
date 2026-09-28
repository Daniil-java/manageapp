package com.kuklin.manageapp.bots.caloriebot.entities.utm;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "utm_clicks")
@Data
@Accessors(chain = true)
public class UtmClick {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "utm_link_id")
    private Long utmLinkId;

    // ID пользователя из Telegram (Long userId)
    private Long userId;

    // Флаг: был ли это абсолютно новый пользователь для бота
    private boolean isNewUser;

    @CreationTimestamp
    private Instant clickedAt;
}
