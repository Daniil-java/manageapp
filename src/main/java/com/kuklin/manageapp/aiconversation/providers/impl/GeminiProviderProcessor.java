package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.integrations.GeminiFeignClient;
import com.kuklin.manageapp.aiconversation.models.AiResponse;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.aiconversation.models.gemini.GeminiRequest;
import com.kuklin.manageapp.aiconversation.models.gemini.GeminiResponse;
import com.kuklin.manageapp.aiconversation.providers.ProviderProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class GeminiProviderProcessor implements ProviderProcessor {

    private final GeminiFeignClient geminiFeignClient;

    private static final Set<String> SUPPORTED_IMAGE_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    @Override
    public ProviderVariant getProviderName() {
        return ProviderVariant.GEMINI;
    }

    /**
     * imgUrl — data URL вида "data:image/jpeg;base64,...."
     */
    @Override
    public AiResponse fetchResponsePhotoOrNull(String imgUrl, String content, ChatModel chatModel, String aiKey) {
        try {
            // 1) Разбираем data URL на mime и чистые base64-данные
            DataUrlParts data = splitDataUrl(imgUrl);

            // 2) Валидируем MIME
            String mime = normalizeMime(data.mimeType);
            if (!SUPPORTED_IMAGE_MIME.contains(mime)) {
                throw new IllegalArgumentException("Unsupported image mime-type: " + mime);
            }

            // 3) Собираем запрос Gemini: текст + inline_data (base64)
            GeminiRequest request = GeminiRequest.createUserRequestWithInlineImage(
                    content, mime, data.base64
            );

            // 4) Вызов модели Gemini
            GeminiResponse response = geminiFeignClient.generate(chatModel.getName(), aiKey, request);

            // 5) Маппим в AiResponse
            String text = (response != null) ? response.firstTextOrEmpty() : "";
            return new AiResponse()
                    .setModel(chatModel)
                    .setContent(text);

        } catch (Exception e) {
            log.error("Gemini photo call failed", e);
            return null;
        }
    }

    // ===== Внутренние утилиты без регексов/внешних классов =====

    /**
     * Ожидается строка вида:
     * data:<mime>;base64,<payload>
     * Пример: data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ...
     */
    private DataUrlParts splitDataUrl(String dataUrl) {
        if (dataUrl == null) {
            log.error("imgUrl (data URL) is null");
            throw new IllegalArgumentException("imgUrl (data URL) is null");
        }
        String s = dataUrl.trim();
        if (!s.startsWith("data:")) {
            log.error("imgUrl must be a data URL: " + s);
            throw new IllegalArgumentException("imgUrl must be a data URL: " + s);
        }

        int commaIdx = s.indexOf(',');
        if (commaIdx < 0) {
            log.error("Invalid data URL, no comma found");
            throw new IllegalArgumentException("Invalid data URL, no comma found");
        }

        String header = s.substring(5, commaIdx); // после "data:"
        String payload = s.substring(commaIdx + 1);
        if (payload.isBlank()) {
            log.error("Empty base64 payload in data URL");
            throw new IllegalArgumentException("Empty base64 payload in data URL");
        }

        // header обычно "image/jpeg;base64" или "image/png;base64"
        String[] headerParts = header.split(";");
        if (headerParts.length == 0) {
            log.error("Invalid data URL header");
            throw new IllegalArgumentException("Invalid data URL header");
        }

        String mime = headerParts[0].trim();
        boolean isBase64 = false;
        for (int i = 1; i < headerParts.length; i++) {
            if ("base64".equalsIgnoreCase(headerParts[i].trim())) {
                isBase64 = true;
                break;
            }
        }
        if (!isBase64) {
            log.error("Data URL must be base64-encoded");
            throw new IllegalArgumentException("Data URL must be base64-encoded");
        }

        return new DataUrlParts(mime, payload);
    }

    private String normalizeMime(String ct) {
        return (ct == null) ? "image/jpeg" : ct.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    private record DataUrlParts(String mimeType, String base64) {}
}
