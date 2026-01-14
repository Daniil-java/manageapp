package com.kuklin.manageapp.aiconversation.models.gemini;

import com.kuklin.manageapp.aiconversation.models.enums.ChatRole;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class GeminiRequest {
    private List<Content> contents;

    public static GeminiRequest createUserRequestWithInlineImage(String text, String mimeType, String base64Data) {
        Content.Part textPart = new Content.Part().setText(text);
        Content.Part.InlineData inline = new Content.Part.InlineData()
                .setMimeType(mimeType)
                .setData(base64Data);
        Content.Part imagePart = new Content.Part().setInlineData(inline);

        Content content = new Content()
                .setRole(ChatRole.USER.getValue())
                .setParts(List.of(textPart, imagePart));

        return new GeminiRequest().setContents(List.of(content));
    }

}
