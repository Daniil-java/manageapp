package com.kuklin.manageapp.common.library.models.openai;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class Message {
    private String role;
    private String content;


    public static List<Message> getFirstMessage(String content) {
        return List.of(new Message()
                .setContent(content)
                .setRole(ChatRole.USER.getValue())
        );
    }

}
