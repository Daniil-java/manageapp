package com.kuklin.manageapp.bots.caloriebot.models;

import com.kuklin.manageapp.payment.entities.UserSubscription;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SubscriptionStatusDto {
    private UserSubscription.Status status;
    private String planName;
    private String expiryDate; // Красиво отформатированная дата
    private String statusMessage; // Например: "Активна до 15.06.2026"
}
