package com.kuklin.manageapp.bots.hhparserbot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_filters")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class WorkFilter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long hhUserInfoId;

    private String url;

    @CreationTimestamp
    private LocalDateTime created;
}

