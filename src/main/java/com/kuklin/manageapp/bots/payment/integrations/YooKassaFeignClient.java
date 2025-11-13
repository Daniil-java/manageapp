package com.kuklin.manageapp.bots.payment.integrations;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(
        name = "yooKassa",
        url = "${yookassa.base-url:https://api.yookassa.ru}",
        configuration = com.kuklin.manageapp.bots.payment.configurations.YooKassaClientConfig.class
)
public interface YooKassaFeignClient {
    @GetMapping(value = "/v3/payments/{id}", produces = "application/json")
    Map<String, Object> getPayment(@PathVariable("id") String id);
}
