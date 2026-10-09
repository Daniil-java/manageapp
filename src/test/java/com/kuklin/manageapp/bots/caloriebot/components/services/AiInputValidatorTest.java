package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.configurations.AiInputLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiInputValidatorTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] HEIC = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};

    private AiInputLimitsProperties limits;
    private AiInputValidator validator;

    @BeforeEach
    void setUp() {
        limits = new AiInputLimitsProperties();
        limits.setTextMaxLength(10);
        limits.setPhotoCommentMaxLength(5);
        limits.setPhotoMaxSize(DataSize.ofBytes(100));
        limits.setPhotoFormats(List.of("jpeg", "png", "webp", "gif"));
        limits.setVoiceMaxSize(DataSize.ofBytes(100));
        limits.setVoiceMaxDuration(Duration.ofMinutes(2));
        limits.setVoiceFormats(List.of("ogg", "webm", "mp4", "m4a", "mp3", "wav"));
        limits.setRequestMaxSize(DataSize.ofMegabytes(8));
        validator = new AiInputValidator(limits);
    }

    // ── Текст ─────────────────────────────────────────────

    @Test
    void textWithinLimitPasses() {
        validator.validateText("0123456789");
    }

    @Test
    void textOverLimitIsRejectedWithLimitInMessage() {
        assertThatThrownBy(() -> validator.validateText("01234567890"))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> {
                    ErrorResponseException ex = (ErrorResponseException) e;
                    assertThat(ex.getErrorStatus()).isEqualTo(ErrorStatus.TEXT_TOO_LONG);
                    assertThat(ex.getClientMessage()).contains("10");
                });
    }

    // ── Фото ─────────────────────────────────────────────

    @Test
    void photoGetsRealMimeTypeFromContent() {
        assertThat(validator.toPhotoDataUrl(b64(JPEG), null)).startsWith("data:image/jpeg;base64,");
        assertThat(validator.toPhotoDataUrl(b64(PNG), null)).startsWith("data:image/png;base64,");
        assertThat(validator.toPhotoDataUrl(b64(WEBP), "обед")).startsWith("data:image/webp;base64,");
    }

    @Test
    void photoDataUrlPrefixFromClientIsReplaced() {
        assertThat(validator.toPhotoDataUrl("data:image/jpeg;base64," + b64(PNG), null))
                .isEqualTo("data:image/png;base64," + b64(PNG));
    }

    @Test
    void heicPhotoIsUnsupported() {
        assertStatus(() -> validator.toPhotoDataUrl(b64(HEIC), null), ErrorStatus.UNSUPPORTED_IMAGE_FORMAT);
    }

    @Test
    void tooLargePhotoIsRejected() {
        byte[] big = new byte[101];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);
        assertStatus(() -> validator.toPhotoDataUrl(b64(big), null), ErrorStatus.IMAGE_TOO_LARGE);
    }

    @Test
    void hugePhotoIsRejectedBeforeDecoding() {
        assertStatus(() -> validator.toPhotoDataUrl("A".repeat(10_000), null), ErrorStatus.IMAGE_TOO_LARGE);
    }

    @Test
    void longPhotoCommentIsRejected() {
        assertStatus(() -> validator.toPhotoDataUrl(b64(JPEG), "123456"), ErrorStatus.PHOTO_COMMENT_TOO_LONG);
    }

    @Test
    void brokenBase64IsInvalidFile() {
        assertStatus(() -> validator.toPhotoDataUrl("not base64!!", null), ErrorStatus.INVALID_FILE);
        assertStatus(() -> validator.toPhotoDataUrl("", null), ErrorStatus.INVALID_FILE);
    }

    // ── Голос ─────────────────────────────────────────────

    @Test
    void voiceFormatAcceptsExtensionOrMime() {
        assertThat(validator.toVoiceInput(b64(new byte[]{1, 2, 3}), "webm").fileName()).isEqualTo("audio.webm");
        AiInputValidator.VoiceInput fromMime = validator.toVoiceInput(b64(new byte[]{1, 2, 3}), "audio/webm;codecs=opus");
        assertThat(fromMime.fileName()).isEqualTo("audio.webm");
        assertThat(fromMime.mimeType()).isEqualTo("audio/webm");
        assertThat(validator.toVoiceInput(b64(new byte[]{1}), "audio/mpeg").fileName()).isEqualTo("audio.mp3");
        assertThat(validator.toVoiceInput(b64(new byte[]{1}), "MP4").mimeType()).isEqualTo("audio/mp4");
    }

    @Test
    void unknownVoiceFormatIsRejected() {
        assertStatus(() -> validator.toVoiceInput(b64(new byte[]{1}), "exe"), ErrorStatus.UNSUPPORTED_AUDIO_FORMAT);
        assertStatus(() -> validator.toVoiceInput(b64(new byte[]{1}), null), ErrorStatus.UNSUPPORTED_AUDIO_FORMAT);
    }

    @Test
    void tooLargeVoiceIsRejected() {
        assertStatus(() -> validator.toVoiceInput(b64(new byte[101]), "ogg"), ErrorStatus.AUDIO_TOO_LARGE);
    }

    @Test
    void botVoiceDurationLimit() {
        assertThat(validator.isVoiceDurationAllowed(120)).isTrue();
        assertThat(validator.isVoiceDurationAllowed(121)).isFalse();
        assertThat(validator.isVoiceDurationAllowed(null)).isTrue();
        assertThat(validator.voiceMaxDurationText()).isEqualTo("2 мин");
    }

    // ── Helpers ─────────────────────────────────────────────

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void assertStatus(Runnable call, ErrorStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ErrorResponseException.class)
                .extracting(e -> ((ErrorResponseException) e).getErrorStatus())
                .isEqualTo(status);
    }
}
