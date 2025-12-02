package com.kuklin.manageapp.bots.kworkparser.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "user_url")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserUrl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long telegramId;
    private Long urlId;
}
