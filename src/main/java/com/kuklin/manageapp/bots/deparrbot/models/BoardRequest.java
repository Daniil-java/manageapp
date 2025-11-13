package com.kuklin.manageapp.bots.deparrbot.models;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class BoardRequest {
    private String departure;
    private String arrival;
    private LocalDateTime date;
}
