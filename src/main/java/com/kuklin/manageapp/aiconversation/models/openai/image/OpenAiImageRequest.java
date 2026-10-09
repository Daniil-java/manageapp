package com.kuklin.manageapp.aiconversation.models.openai.image;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
public class OpenAiImageRequest {

    public static final String MODEL_GPT_IMAGE_1 = "gpt-image-1";

    public static final String SIZE_512 = "512x512";
    public static final String SIZE_1024 = "1024x1024";

    // новые форматы (чаще используются сейчас)
    public static final String SIZE_1024x1792 = "1024x1792"; // вертикальная
    public static final String SIZE_1792x1024 = "1792x1024"; // горизонтальная

    private String prompt;
    private String model;   // например "gpt-image-1"
    private String size;    // "1024x1024"
}
