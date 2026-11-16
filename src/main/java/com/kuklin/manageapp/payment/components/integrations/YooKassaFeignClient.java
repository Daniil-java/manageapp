package com.kuklin.manageapp.payment.components.integrations;

import com.kuklin.manageapp.payment.configurations.YooKassaClientConfig;
import com.kuklin.manageapp.payment.models.yookassa.YookassaCreatePaymentRequest;
import com.kuklin.manageapp.payment.models.yookassa.YookassaPaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "yooKassa",
        url = "${yookassa.base-url:https://api.yookassa.ru}",
        configuration = YooKassaClientConfig.class
)
public interface YooKassaFeignClient {
    // Создание платежа. ВАЖНО: прокидываем заголовок идемпотентности.
    @PostMapping(value = "/v3/payments", consumes = "application/json", produces = "application/json")
    YookassaPaymentResponse createPayment(@RequestBody YookassaCreatePaymentRequest body,
                                          @RequestHeader("Idempotence-Key") String idempotenceKey);

    @GetMapping(value = "/v3/payments/{id}", produces = "application/json")
    YookassaPaymentResponse getPayment(@PathVariable("id") String id);
}
