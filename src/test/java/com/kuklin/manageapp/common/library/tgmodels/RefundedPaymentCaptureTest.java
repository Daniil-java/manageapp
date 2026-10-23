package com.kuklin.manageapp.common.library.tgmodels;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * refunded_payment достаётся из ответа getUpdates тем же разбором, что у ботов (ObjectMapper библиотеки),
 * а остальной апдейт разбирается как раньше.
 */
class RefundedPaymentCaptureTest {

    @BeforeAll
    static void install() {
        RefundedPaymentCapture.install();
    }

    @Test
    void refundedPaymentIsCapturedByUpdateId() throws Exception {
        List<Update> updates = new GetUpdates().deserializeResponse("""
                {"ok":true,"result":[
                  {"update_id":901,"message":{"message_id":11,"date":1,
                    "chat":{"id":7,"type":"private"},"from":{"id":7,"is_bot":false,"first_name":"A"},
                    "refunded_payment":{"currency":"XTR","total_amount":299,
                      "invoice_payload":"premium-5-42","telegram_payment_charge_id":"charge-1"}}},
                  {"update_id":902,"message":{"message_id":12,"date":1,
                    "chat":{"id":7,"type":"private"},"from":{"id":7,"is_bot":false,"first_name":"A"},
                    "text":"hi"}}
                ]}
                """);

        Update refund = updates.get(0);
        assertThat(refund.getMessage().getMessageId()).isEqualTo(11);
        assertThat(refund.getMessage().getFrom().getId()).isEqualTo(7L);
        assertThat(RefundedPaymentCapture.findOrNull(refund))
                .isEqualTo(new RefundedPayment("XTR", 299, "premium-5-42", "charge-1"));

        Update text = updates.get(1);
        assertThat(text.getMessage().getText()).isEqualTo("hi");
        assertThat(RefundedPaymentCapture.findOrNull(text)).isNull();
    }

    @Test
    void updateWithoutRefundIsNotPaymentUpdate() throws Exception {
        List<Update> updates = new GetUpdates().deserializeResponse("""
                {"ok":true,"result":[{"update_id":903,"message":{"message_id":13,"date":1,
                  "chat":{"id":7,"type":"private"},"from":{"id":7,"is_bot":false,"first_name":"A"},"text":"/start"}}]}
                """);

        assertThat(TelegramBot.isPaymentUpdate(updates.get(0))).isFalse();
    }
}
