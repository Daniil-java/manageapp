package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalorieAccessService {
    private static final Long RESPONSE_LIMIT = 10L;

    private final CommonPaymentFacade commonPaymentFacade;
    private final TelegramUserService telegramUserService;

    public boolean checkAccess(TelegramUser user) {
        if (user.getResponseCount() <= RESPONSE_LIMIT) {
            return true;
        }

        boolean active = commonPaymentFacade.hasActiveSubscription(
                user.getBotIdentifier(),
                user.getTelegramId()
        );

        if (active) {
            return true;
        }

        return false;
    }

    public void incrementResponses(TelegramUser user) {
        telegramUserService.save(
                user.setResponseCount(user.getResponseCount() + 1)
        );
    }
}
