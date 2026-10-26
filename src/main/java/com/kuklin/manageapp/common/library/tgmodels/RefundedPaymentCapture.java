package com.kuklin.manageapp.common.library.tgmodels;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * telegrambots 6.9 не знает поля message.refunded_payment и молча выбрасывает его при разборе апдейта.
 * Перехват: в ObjectMapper библиотеки (им GetUpdates разбирает ответ) десериализатор Update сначала читает
 * JSON целиком, достаёт refunded_payment и запоминает по update_id. Обработчики берут его через findOrNull.
 * <p>
 * install() — до регистрации ботов: десериализатор Update кешируется при первом getUpdates.
 * Не встал (библиотеку обновили и поля нет) — возвраты всё равно поймает суточная сверка getStarTransactions.
 */
@Slf4j
public final class RefundedPaymentCapture {
    // Обработчик может и не забрать (апдейт пришёл боту без платежей) — храним последние, не все
    private static final int MAX_KEPT = 1000;
    private static final Map<Integer, RefundedPayment> BY_UPDATE_ID = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, RefundedPayment> eldest) {
                    return size() > MAX_KEPT;
                }
            });

    private RefundedPaymentCapture() {
    }

    public static void install() {
        try {
            Field field = PartialBotApiMethod.class.getDeclaredField("OBJECT_MAPPER");
            field.setAccessible(true);
            install((ObjectMapper) field.get(null));
            log.info("refunded_payment capture installed");
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("refunded_payment capture not installed, refunds are caught by reconciliation only", e);
        }
    }

    static void install(ObjectMapper mapper) {
        mapper.registerModule(new SimpleModule("refunded-payment-capture")
                .setDeserializerModifier(new BeanDeserializerModifier() {
                    @Override
                    public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                                                                  BeanDescription beanDesc,
                                                                  JsonDeserializer<?> deserializer) {
                        return Update.class.equals(beanDesc.getBeanClass())
                                ? new UpdateDeserializer(deserializer)
                                : deserializer;
                    }
                }));
    }

    /** Возврат из этого апдейта или null — апдейт не о возврате. */
    public static RefundedPayment findOrNull(Update update) {
        return update.getUpdateId() == null ? null : BY_UPDATE_ID.get(update.getUpdateId());
    }

    static RefundedPayment buildOrNull(JsonNode update) {
        JsonNode refunded = update.path("message").path("refunded_payment");
        if (!refunded.isObject()) return null;
        return new RefundedPayment(
                refunded.path("currency").asText(null),
                refunded.path("total_amount").asInt(),
                refunded.path("invoice_payload").asText(null),
                refunded.path("telegram_payment_charge_id").asText(null)
        );
    }

    private static class UpdateDeserializer extends DelegatingDeserializer {

        UpdateDeserializer(JsonDeserializer<?> delegatee) {
            super(delegatee);
        }

        @Override
        protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
            return new UpdateDeserializer(newDelegatee);
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            TokenBuffer buffer = ctxt.bufferAsCopyOfValue(p);
            JsonNode node = ctxt.readTree(buffer.asParserOnFirstToken());
            RefundedPayment refunded = buildOrNull(node);
            if (refunded != null) {
                BY_UPDATE_ID.put(node.path("update_id").asInt(), refunded);
            }
            return _delegatee.deserialize(buffer.asParserOnFirstToken(), ctxt);
        }
    }
}
