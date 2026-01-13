package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.integrations.ClaudeFeignClient;
import com.kuklin.manageapp.aiconversation.models.AiResponse;
import com.kuklin.manageapp.aiconversation.models.claude.ClaudeRequest;
import com.kuklin.manageapp.aiconversation.models.claude.ClaudeResponse;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.aiconversation.providers.ProviderProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class ClaudeProviderProcessor implements ProviderProcessor {

    private final ClaudeFeignClient claudeFeignClient;

    // Добавь, если нужно больше форматов
    private static final Set<String> SUPPORTED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    @Override
    public ProviderVariant getProviderName() {
        return ProviderVariant.CLAUDE;
    }

    @Override
    public AiResponse fetchResponsePhotoOrNull(String imgUrlOrDataUrl, String content, ChatModel chatModel, String aiKey) {
        int maxTokensLimit = 1024;
        String anthropicApiVersion = "2023-06-01";
        try {
            String modelName = chatModel.getName();
            ClaudeRequest req;

            // Если это data-URL → base64-вариант
            if (imgUrlOrDataUrl.startsWith("data:")) {
                DataImage di = parseDataImage(imgUrlOrDataUrl);
                req = ClaudeRequest.ofBase64Image(
                        modelName,
                        maxTokensLimit,
                        content,
                        di.mime(),
                        di.base64()
                );
            } else {
                // Иначе считаем, что это обычный URL (http/https)
                req = ClaudeRequest.ofUrlImage(
                        modelName,
                        maxTokensLimit,
                        content,
                        imgUrlOrDataUrl
                );
            }

            ClaudeResponse resp = claudeFeignClient.messages(
                    aiKey,
                    anthropicApiVersion,
                    req
            );

            return new AiResponse()
                    .setModel(chatModel)
                    .setContent(resp != null ? resp.firstTextOrEmpty() : "");
        } catch (Exception e) {
            log.error("Claude photo call failed", e);
            return null;
        }
    }

    /**
     * Ожидается строка вида: data:<mime>;base64,<payload>
     * Пример: data:image/jpeg;base64,/9j/4AAQ...
     */
    private DataImage parseDataImage(String dataUrl) {
        String s = dataUrl.trim();
        if (!s.startsWith("data:")) {
            log.error("imgUrl must be a data URL (starts with 'data:')");
            throw new IllegalArgumentException();
        }

        int commaIdx = s.indexOf(',');
        if (commaIdx < 0) {
            log.error("Invalid data URL: no comma separator");
            throw new IllegalArgumentException();
        }

        // header: "<mime>;base64" (возможны доп. параметры)
        String header = s.substring(5, commaIdx); // после "data:"
        String payload = s.substring(commaIdx + 1);
        if (payload.isBlank()) {
            log.error("Empty base64 payload in data URL");
            throw new IllegalArgumentException();
        }

        // найдём mime до первого ';'
        int semi = header.indexOf(';');
        String mime = (semi >= 0 ? header.substring(0, semi) : header)
                .toLowerCase(Locale.ROOT)
                .trim();

        // убедимся, что есть маркер base64
        if (semi < 0 || !header.substring(semi + 1).toLowerCase(Locale.ROOT).contains("base64")) {
            log.error("Data URL must be base64-encoded");
            throw new IllegalArgumentException();
        }

        mime = normalizeMime(mime);
        validateMime(mime);

        // убираем пробелы/переводы строк из base64 (на всякий случай)
        String cleanBase64 = payload.replaceAll("\\s+", "");

        return new DataImage(mime, cleanBase64);
    }

    private String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) return "image/jpeg";
        if ("image/jpg".equals(mime)) return "image/jpeg"; // унификация
        return mime;
    }

    private void validateMime(String mime) {
        if (!SUPPORTED_MIME.contains(mime)) {
            log.error("Unsupported image mime-type: " + mime);
            throw new IllegalArgumentException("Unsupported image mime-type: " + mime);
        }
    }

    private record DataImage(String mime, String base64) {}
}
