package com.kuklin.manageapp.payment.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

/**
 * getStarTransactions — все движения звёзд бота, от старых к новым. В telegrambots 6.9 метода нет.
 */
public class StarTransactionsClient {
    private static final int PAGE_SIZE = 100;
    // Предохранитель от бесконечного цикла: 100 000 транзакций
    private static final int MAX_PAGES = 1000;

    private final String botToken;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StarTransactionsClient(String botToken) { this.botToken = botToken; }

    /**
     * charge_id платежей, по которым звёзды ушли обратно. У возврата id транзакции совпадает с id исходной,
     * а он — с telegram_payment_charge_id; входящая транзакция — с source, исходящая — с receiver.
     */
    public Set<String> findRefundedChargeIds() throws IOException, InterruptedException {
        Set<String> refunded = new HashSet<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode transactions = getTransactions(page * PAGE_SIZE);
            for (JsonNode transaction : transactions) {
                if (transaction.has("receiver") && !transaction.has("source")) {
                    refunded.add(transaction.path("id").asText());
                }
            }
            if (transactions.size() < PAGE_SIZE) break;
        }
        return refunded;
    }

    JsonNode getTransactions(int offset) throws IOException, InterruptedException {
        String api = "https://api.telegram.org/bot" + botToken + "/getStarTransactions";
        String body = """
        {
          "offset": %d,
          "limit": %d
        }
        """.formatted(offset, PAGE_SIZE);

        HttpRequest req = HttpRequest.newBuilder(URI.create(api))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(resp.body());
        if (!root.path("ok").asBoolean()) {
            throw new IOException("getStarTransactions failed: HTTP " + resp.statusCode() + " "
                    + root.path("description").asText());
        }
        return root.path("result").path("transactions");
    }
}
