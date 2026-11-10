package com.kuklin.manageapp.aiconversation.models.openai.image;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
public class OpenAiImageRequest {

    // gpt-image-1 отключён OpenAI 23.10.2026
    public static final String MODEL_GPT_IMAGE_2_5_FLARE = "gpt-image-2.5-flare";

    public static final String SIZE_512 = "512x512";
    public static final String SIZE_1024 = "1024x1024";

    // новые форматы (чаще используются сейчас)
    public static final String SIZE_1024x1792 = "1024x1792"; // вертикальная
    public static final String SIZE_1792x1024 = "1792x1024"; // горизонтальная

    private String prompt;
    private String model;   // например "gpt-image-2.5-flare"
    private String size;    // "1024x1024"
}
