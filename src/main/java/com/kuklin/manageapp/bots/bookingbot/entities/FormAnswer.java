package com.kuklin.manageapp.bots.bookingbot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "forms")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class FormAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bookingId;
    private String question;
    private String answer;

}
