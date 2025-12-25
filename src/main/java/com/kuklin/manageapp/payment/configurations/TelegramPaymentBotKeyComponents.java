package com.kuklin.manageapp.payment.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class TelegramPaymentBotKeyComponents {
    private final String key;
    private final String aiKey;
    private final String providerToken;
    private final String shopId;
    private final String shopSecretKey;

    @Autowired
    public TelegramPaymentBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("PAYMENT_BOT_TOKEN");
        log.info("Generation key initiated (PAYMENT_BOT_TOKEN)");
        this.aiKey = environment.getProperty("PAYMENT_GENERATION_TOKEN");
        log.info("Ai key initiated (PAYMENT_GENERATION_TOKEN)");

        this.providerToken = environment.getProperty("PAYMENT_PROVIDER_TOKEN");
        this.shopId = environment.getProperty("PAYMENT_SHOP_ID");
        this.shopSecretKey = environment.getProperty("PAYMENT_SHOP_SECRET_KEY");

    }
}
