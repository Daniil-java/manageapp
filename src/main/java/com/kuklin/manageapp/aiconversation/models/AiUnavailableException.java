package com.kuklin.manageapp.aiconversation.models;

/**
 * ИИ не ответил: провайдер вернул ошибку (401 — ключ, 429 — лимиты/баланс, 5xx), запрос не дошёл
 * или оборвался по таймауту, пришёл пустой ответ. Это не «ИИ не нашёл еду» — ответа нет вообще.
 * Боты ловят его и говорят пользователю «ИИ сейчас не отвечает, попробуйте позже».
 * status — HTTP-код провайдера; 0 — ответа не было (сеть, таймаут) или он пустой.
 */
public class AiUnavailableException extends RuntimeException {
    private final int status;

    public AiUnavailableException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
