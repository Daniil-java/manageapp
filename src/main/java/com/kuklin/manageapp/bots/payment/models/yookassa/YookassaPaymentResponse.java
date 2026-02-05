package com.kuklin.manageapp.bots.payment.models.yookassa;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YookassaPaymentResponse {
    private String id;
    private String status;          // waiting_for_capture | succeeded | canceled | ...
    private Boolean paid;

    private Amount amount;
    private String description;
    private Confirmation confirmation;
    private Metadata metadata;
}
