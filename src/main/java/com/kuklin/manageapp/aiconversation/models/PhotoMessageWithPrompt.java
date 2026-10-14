package com.kuklin.manageapp.aiconversation.models;

import com.kuklin.manageapp.aiconversation.models.enums.ChatRole;
import com.kuklin.manageapp.aiconversation.models.openai.ContentPart;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class PhotoMessageWithPrompt {
    private String role;
    private List<ContentPart> content;

    public static List<PhotoMessageWithPrompt> getFirstMessage(String content) {
        return List.of(new PhotoMessageWithPrompt()
                .setContent(List.of(ContentPart.getTextContentPart(content)))
                .setRole(ChatRole.USER.getValue())
        );
    }

    public static List<PhotoMessageWithPrompt> getMessageList(String content, String imageUrl) {
        List<ContentPart> contentParts = List.of(
                ContentPart.getTextContentPart(content),
                ContentPart.getImgContentPart(imageUrl)
        );
        return List.of(
                new PhotoMessageWithPrompt()
                        .setContent(contentParts)
                        .setRole(ChatRole.USER.getValue())
        );
    }
}
