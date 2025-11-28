package com.kuklin.manageapp.bots.deparrbot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_flights")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserFlight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @CreationTimestamp
    @Column(name = "subscribed_at")
    private LocalDateTime subscribedAt;
    @Column(name = "notifications_enabled")
    private boolean notificationsEnabled;


}
