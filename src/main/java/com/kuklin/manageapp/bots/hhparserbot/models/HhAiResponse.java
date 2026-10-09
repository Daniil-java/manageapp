package com.kuklin.manageapp.bots.hhparserbot.models;

import lombok.Data;

import java.util.List;

@Data
public class HhAiResponse {
    private String generatedDescription;
    private List<String> keySkills;
    private List<String> strictlyRequiredSkills;
}
