package com.kuklin.manageapp.bots.payment.telegram.handlers.providerprocessors;

public record ProviderResult(
        String url,          //Ссылка на оплату в провайдере
        String paymentId     // id платёжа у провайдера
) {}
