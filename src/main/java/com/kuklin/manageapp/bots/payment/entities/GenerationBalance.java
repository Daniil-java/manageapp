package com.kuklin.manageapp.bots.payment.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "generation_balance")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class GenerationBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;
    private Long generationRequests;

    public GenerationBalance subtract(Long request) {
        generationRequests = generationRequests - request;
        return this;
    }

    public GenerationBalance topUp(Long request) {
        generationRequests = generationRequests + request;
        return this;
    }

}
