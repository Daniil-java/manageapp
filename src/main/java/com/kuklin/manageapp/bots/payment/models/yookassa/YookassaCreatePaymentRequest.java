package com.kuklin.manageapp.bots.payment.models.yookassa;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

//Запрос для ЮКассы на создание платежа
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YookassaCreatePaymentRequest {

    private Boolean capture;

    private Amount amount;
    private String description;
    private Confirmation confirmation;
    private Metadata metadata;

}
