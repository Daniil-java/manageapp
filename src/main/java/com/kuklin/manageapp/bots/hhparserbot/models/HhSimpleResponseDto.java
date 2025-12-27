package com.kuklin.manageapp.bots.hhparserbot.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class HhSimpleResponseDto {
    private String url;
    private Long hhId;
}
