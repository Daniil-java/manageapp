package com.kuklin.manageapp.bots.kworkparser.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "user_kwork_notification")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserKworkNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long telegramId;
    private Long kworkId;
    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Status {
        CREATED, SENT, ERROR
    }
}
