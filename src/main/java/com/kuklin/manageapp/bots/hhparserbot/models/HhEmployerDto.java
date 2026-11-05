package com.kuklin.manageapp.bots.hhparserbot.models;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HhEmployerDto {
    private Long id;
    private String description;
}
