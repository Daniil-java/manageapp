package com.kuklin.manageapp.bots.payment.models.yookassa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Metadata {
    private String userId;
    private Long telegramChatId;
    private String productCode;
    private String orderId;
}
