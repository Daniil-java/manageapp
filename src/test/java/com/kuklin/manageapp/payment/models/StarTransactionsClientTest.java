package com.kuklin.manageapp.payment.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Возврат — исходящая транзакция (receiver без source) с id исходного платежа; страницы читаются до неполной.
 */
class StarTransactionsClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void outgoingTransactionsAreRefunds() throws Exception {
        List<Integer> offsets = new ArrayList<>();
        ArrayNode firstPage = MAPPER.createArrayNode();
        for (int i = 0; i < 99; i++) {
            firstPage.add(MAPPER.readTree("""
                    {"id":"paid-%d","amount":299,"date":1,"source":{"type":"user","user":{"id":7}}}
                    """.formatted(i)));
        }
        firstPage.add(MAPPER.readTree("""
                {"id":"paid-5","amount":299,"date":2,"receiver":{"type":"user","user":{"id":7}}}
                """));
        JsonNode secondPage = MAPPER.readTree("""
                [{"id":"paid-7","amount":299,"date":3,"receiver":{"type":"user","user":{"id":7}}},
                 {"id":"withdrawal-1","amount":1000,"date":4,"receiver":{"type":"fragment"}}]
                """);

        StarTransactionsClient client = new StarTransactionsClient("token") {
            @Override
            JsonNode getTransactions(int offset) {
                offsets.add(offset);
                return offset == 0 ? firstPage : secondPage;
            }
        };

        assertThat(client.findRefundedChargeIds()).containsExactlyInAnyOrder("paid-5", "paid-7", "withdrawal-1");
        assertThat(offsets).containsExactly(0, 100);
    }
}
