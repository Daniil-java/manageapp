package com.kuklin.manageapp.bots.aiassistantcalendar.models;

import lombok.Data;

@Data
public class SpeechRequest {
    String input;
    String model;
    String voice;
}
