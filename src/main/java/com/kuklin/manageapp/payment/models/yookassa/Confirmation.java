package com.kuklin.manageapp.payment.models.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Confirmation {
    private String type;
    @JsonProperty("confirmation_url")
    private String confirmationUrl;
    @JsonProperty("return_url")
    private String returnUrl;
}
