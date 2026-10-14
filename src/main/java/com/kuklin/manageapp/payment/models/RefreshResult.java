package com.kuklin.manageapp.payment.models;

import com.kuklin.manageapp.payment.entities.UserSubscription;

import java.util.List;

public record RefreshResult(
        List<UserSubscription> expired,
        List<UserSubscription> activated
) {}
