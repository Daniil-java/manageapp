package com.kuklin.manageapp.payment.components.providerprocessors;

public record ProviderResult(
        String url,          //Ссылка на оплату в провайдере
        String paymentId     // id платёжа у провайдера
) {}
