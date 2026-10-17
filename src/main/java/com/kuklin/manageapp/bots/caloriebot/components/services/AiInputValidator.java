package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.configurations.AiInputLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * Проверяет ввод пользователя перед отправкой в ИИ: длину текста, размер и формат фото и аудио.
 * Лимиты берутся из {@link AiInputLimitsProperties} (application.yaml, calorie.ai-input).
 * При нарушении бросает {@link ErrorResponseException} с конкретным лимитом в сообщении —
 * GlobalExceptionHandler превращает его в ответ 400 / 413 / 415 с errorCode.
 *
 * Используется в DishService для эндпоинтов мини-аппы и сайта (/calorie/dishes/text, /photo, /voice)
 * и в DishUpdateHandler бота (только длительность голосового — остальное ограничивает сам Telegram).
 */
@Component
@RequiredArgsConstructor
public class AiInputValidator {

    // Синонимы форматов аудио. Фронт может прислать формат по-разному:
    // расширением ("mp3") или MIME-типом ("audio/mpeg", "audio/x-m4a").
    // После того как normalizeAudioFormat отрежет "audio/", здесь остаются неочевидные названия —
    // приводим их к одному имени из белого списка voice-formats в yaml.
    private static final Map<String, String> AUDIO_FORMAT_ALIASES = Map.of(
            "mpeg", "mp3",   // audio/mpeg — это mp3
            "x-m4a", "m4a",  // audio/x-m4a — так m4a называет Safari
            "x-wav", "wav",
            "wave", "wav"
    );

    // Формат → MIME-тип, с которым файл уходит в Whisper (Content-Type в multipart-запросе).
    // Whisper определяет формат по расширению имени файла (audio.webm), MIME — для порядка.
    private static final Map<String, String> AUDIO_MIME_TYPES = Map.of(
            "ogg", "audio/ogg",    // голосовые Telegram
            "webm", "audio/webm",  // запись в Chrome / Android
            "mp4", "audio/mp4",    // запись в Safari / iPhone
            "m4a", "audio/mp4",
            "mp3", "audio/mpeg",
            "wav", "audio/wav"
    );

    private final AiInputLimitsProperties limits;

    /**
     * Аудио, готовое к отправке на распознавание.
     *
     * @param bytes    декодированный файл
     * @param fileName имя файла с правильным расширением, например audio.webm
     * @param mimeType MIME-тип, например audio/webm
     */
    public record VoiceInput(byte[] bytes, String fileName, String mimeType) {
    }

    // ── Текст ─────────────────────────────────────────────

    /** Текстовое описание блюда: не длиннее text-max-length символов. */
    public void validateText(String text) {
        int max = limits.getTextMaxLength();
        if (text != null && text.length() > max) {
            throw new ErrorResponseException(ErrorStatus.TEXT_TOO_LONG,
                    String.format("Text is too long. Maximum is %d characters.", max));
        }
    }

    // ── Фото ─────────────────────────────────────────────

    /**
     * Проверяет фото и подпись к нему.
     * Порядок: подпись → размер → декодирование Base64 → формат по содержимому.
     *
     * @param base64Image фото в Base64 (допускается и data URL — префикс отбрасывается)
     * @return data URL с реальным типом картинки, например data:image/png;base64,... — в таком виде фото уходит в OpenAI
     */
    public String toPhotoDataUrl(String base64Image, String comment) {
        // 1. Подпись к фото — тоже текст, который уходит в промпт
        int maxComment = limits.getPhotoCommentMaxLength();
        if (comment != null && comment.length() > maxComment) {
            throw new ErrorResponseException(ErrorStatus.PHOTO_COMMENT_TOO_LONG,
                    String.format("Photo comment is too long. Maximum is %d characters.", maxComment));
        }

        // 2. Размер + корректность Base64
        String base64 = stripDataUrlPrefix(base64Image);
        DataSize maxSize = limits.getPhotoMaxSize();
        byte[] bytes = decodeWithinLimit(base64, maxSize, ErrorStatus.IMAGE_TOO_LARGE,
                String.format("Photo is too large. Maximum is %s.", formatSize(maxSize)));

        // 3. Формат определяем по первым байтам файла — клиенту не верим
        String format = detectImageFormat(bytes);
        if (format == null || !limits.getPhotoFormats().contains(format)) {
            throw new ErrorResponseException(ErrorStatus.UNSUPPORTED_IMAGE_FORMAT,
                    "Unsupported photo format. Allowed: " + String.join(", ", limits.getPhotoFormats()) + ".");
        }
        return "data:image/" + format + ";base64," + base64;
    }

    // ── Голос ─────────────────────────────────────────────

    /**
     * Проверяет голосовое из мини-аппы / сайта.
     * Порядок: формат (белый список) → размер → декодирование Base64.
     * Длительность записи сервер проверить не может (клиент может прислать что угодно),
     * поэтому ограничиваем размер файла: voice-max-size ≈ voice-max-duration.
     *
     * @param format расширение (webm) или MIME (audio/webm;codecs=opus)
     */
    public VoiceInput toVoiceInput(String base64Audio, String format) {
        // 1. Формат: приводим к короткому имени и сверяем с voice-formats из yaml
        String normalized = normalizeAudioFormat(format);
        if (normalized == null || !limits.getVoiceFormats().contains(normalized)) {
            throw new ErrorResponseException(ErrorStatus.UNSUPPORTED_AUDIO_FORMAT,
                    "Unsupported audio format. Allowed: " + String.join(", ", limits.getVoiceFormats()) + ".");
        }

        // 2. Размер + корректность Base64
        DataSize maxSize = limits.getVoiceMaxSize();
        byte[] bytes = decodeWithinLimit(stripDataUrlPrefix(base64Audio), maxSize, ErrorStatus.AUDIO_TOO_LARGE,
                String.format("Voice message is too long. Maximum is %s (about %s).",
                        formatSize(maxSize), formatDuration(limits.getVoiceMaxDuration())));

        // 3. Имя файла с правильным расширением — по нему Whisper поймёт формат
        return new VoiceInput(bytes, "audio." + normalized,
                AUDIO_MIME_TYPES.getOrDefault(normalized, "application/octet-stream"));
    }

    /**
     * Бот: Telegram сам сообщает длительность голосового (и ей можно верить),
     * поэтому проверяем её до скачивания файла и вызова ИИ.
     * null (Telegram не прислал длительность) — пропускаем.
     */
    public boolean isVoiceDurationAllowed(Integer durationSeconds) {
        return durationSeconds == null || durationSeconds <= limits.getVoiceMaxDuration().toSeconds();
    }

    /** Лимит длительности голосового по-русски — для сообщений бота: «2 мин», «90 сек». */
    public String voiceMaxDurationText() {
        long seconds = limits.getVoiceMaxDuration().toSeconds();
        return seconds % 60 == 0 ? seconds / 60 + " мин" : seconds + " сек";
    }

    // ── Private ─────────────────────────────────────────────

    /**
     * Декодирует Base64 и проверяет размер файла.
     * Сначала оцениваем размер по длине строки (каждые 4 символа Base64 = 3 байта),
     * чтобы не тратить память на декодирование заведомо слишком большого файла.
     * Затем декодируем (битый Base64 → INVALID_FILE) и проверяем точный размер.
     */
    private byte[] decodeWithinLimit(String base64, DataSize maxSize, ErrorStatus tooLargeStatus, String tooLargeMessage) {
        if (base64 == null || base64.isBlank()) {
            throw new ErrorResponseException(ErrorStatus.INVALID_FILE);
        }
        // +2 — запас на округление: в конце Base64 бывает до двух символов-заполнителей "="
        long estimatedBytes = base64.length() / 4L * 3;
        if (estimatedBytes > maxSize.toBytes() + 2) {
            throw new ErrorResponseException(tooLargeStatus, tooLargeMessage);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ErrorResponseException(ErrorStatus.INVALID_FILE);
        }
        if (bytes.length == 0) {
            throw new ErrorResponseException(ErrorStatus.INVALID_FILE);
        }
        if (bytes.length > maxSize.toBytes()) {
            throw new ErrorResponseException(tooLargeStatus, tooLargeMessage);
        }
        return bytes;
    }

    /**
     * Убирает префикс data URL, если клиент прислал файл целиком из FileReader:
     * "data:image/jpeg;base64,/9j/4AAQ..." → "/9j/4AAQ...". Обычный Base64 возвращается как есть.
     */
    private static String stripDataUrlPrefix(String value) {
        if (value != null && value.startsWith("data:")) {
            int comma = value.indexOf(',');
            return comma >= 0 ? value.substring(comma + 1) : "";
        }
        return value;
    }

    /**
     * Определяет формат картинки по первым байтам («сигнатуре»), а не по тому, что прислал клиент.
     * У каждого формата файл начинается с фиксированных байтов:
     * JPEG — FF D8 FF, PNG — 89 'PNG', GIF — 'GIF8', WebP — 'RIFF' + 4 байта размера + 'WEBP'.
     * Всё остальное (HEIC, BMP, не картинка) → null, то есть формат не поддерживается.
     */
    static String detectImageFormat(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) return "jpeg";
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G')) return "png";
        if (startsWith(b, 0, 'G', 'I', 'F', '8')) return "gif";
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) return "webp";
        return null;
    }

    /** Совпадают ли байты массива начиная с offset с ожидаемой сигнатурой. */
    private static boolean startsWith(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            // & 0xFF: byte в Java знаковый (0xFF = -1), приводим к 0..255 для сравнения
            if ((bytes[offset + i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    /**
     * Приводит формат аудио к короткому имени из белого списка:
     * "audio/webm;codecs=opus" → "webm", "audio/mpeg" → "mp3", ".OGG" → "ogg".
     */
    static String normalizeAudioFormat(String format) {
        if (format == null || format.isBlank()) return null;
        String value = format.trim().toLowerCase(Locale.ROOT);
        int semicolon = value.indexOf(';');                     // отрезаем параметры: ";codecs=opus"
        if (semicolon >= 0) value = value.substring(0, semicolon);
        int slash = value.lastIndexOf('/');                     // отрезаем тип: "audio/"
        if (slash >= 0) value = value.substring(slash + 1);
        if (value.startsWith(".")) value = value.substring(1);  // ".ogg" → "ogg"
        return AUDIO_FORMAT_ALIASES.getOrDefault(value, value); // "mpeg" → "mp3"
    }

    /** Размер для сообщения об ошибке: 5MB → "5 MB", 500KB → "500 KB". */
    private static String formatSize(DataSize size) {
        long mb = size.toMegabytes();
        return mb > 0 && DataSize.ofMegabytes(mb).equals(size) ? mb + " MB" : size.toKilobytes() + " KB";
    }

    /** Длительность для сообщения об ошибке: 2m → "2 min", 90s → "90 s". */
    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        return seconds % 60 == 0 ? seconds / 60 + " min" : seconds + " s";
    }
}
