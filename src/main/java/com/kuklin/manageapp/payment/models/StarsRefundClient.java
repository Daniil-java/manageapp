package com.kuklin.manageapp.payment.models;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class StarsRefundClient {
    private final String botToken;
    private final HttpClient http = HttpClient.newHttpClient();

    public StarsRefundClient(String botToken) { this.botToken = botToken; }

    public boolean refund(long userId, String chargeId) throws Exception {
        String api = "https://api.telegram.org/bot" + botToken + "/refundStarPayment";
        String body = """
        {
          "user_id": %d,
          "telegram_payment_charge_id": "%s"
        }
        """.formatted(userId, chargeId);

        HttpRequest req = HttpRequest.newBuilder(URI.create(api))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() / 100 == 2 && resp.body().contains("\"ok\":true");
    }
}
