package com.kuklin.manageapp.aiconversation.models;

import lombok.Data;

@Data
public class SpeechRequest {
    String input;
    String model;
    String voice;
}
