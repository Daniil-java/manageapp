package com.kuklin.manageapp.bots.payment.models.yookassa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Amount {
    private String value;
    private String currency;
}
