package com.kuklin.manageapp.bots.pomidorotimer.models.openai;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class OpenAiChatCompletionRequest {
    private String model;
    private List<Message> messages;
    private float temperature;

    public static OpenAiChatCompletionRequest makeRequest(String request) {
        //Хардкод. Меня устраивает в моём pet проекте. В промешленной экспл. вынесу в конфиг
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new Message()
                .setRole("user")
                .setContent(request)
        );

        return new OpenAiChatCompletionRequest()
                .setModel("gpt-3.5-turbo")
                .setMessages(messages)
                .setTemperature(0.7f);
    }
}
