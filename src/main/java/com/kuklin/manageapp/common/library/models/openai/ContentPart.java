package com.kuklin.manageapp.common.library.models.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentPart {
    private String type; // "text" или "image_url"
    private String text; // если type=text
    @JsonProperty("image_url")
    private ImageUrl imageUrl; // если type=image_url

    public static ContentPart getTextContentPart(String text) {
        return new ContentPart()
                .setType("text")
                .setText(text);
    }

    public static ContentPart getImgContentPart(String imageUrl) {
        return new ContentPart()
                .setType("image_url")
                .setImageUrl(new ImageUrl().setUrl(imageUrl));
    }

    @Data
    @Accessors(chain = true)
    public static class ImageUrl {
        private String url; // либо https://..., либо data:image/jpeg;base64,...
    }
}
