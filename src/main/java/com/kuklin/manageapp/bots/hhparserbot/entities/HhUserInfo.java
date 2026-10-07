package com.kuklin.manageapp.bots.hhparserbot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "hh_user_info")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class HhUserInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;

    private String info;

    @CreationTimestamp
    private LocalDateTime created;
}

