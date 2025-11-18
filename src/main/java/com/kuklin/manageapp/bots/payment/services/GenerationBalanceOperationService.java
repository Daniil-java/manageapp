package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.GenerationBalanceOperation;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.repositories.GenerationBalanceOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GenerationBalanceOperationService {
    private final GenerationBalanceOperationRepository repository;
    private final GenerationBalanceService generationBalanceService;
    private final PricingPlanService pricingPlanService;

    @Transactional
    public GenerationBalanceOperation increaseBalanceByPayment(Payment payment) {
        //Получение баланса пользователя или налл
        GenerationBalance generationBalance = generationBalanceService
                .getBalanceByTelegramIdOrNull(payment.getTelegramId());
        if (generationBalance == null) {
            //TODO ERROR
        }

        PricingPlan plan = pricingPlanService
                .getPricingPlanByIdOrNull(payment.getPricingPlanId());

        GenerationBalanceOperation operation = createNewBalanceOperationCredit(
                GenerationBalanceOperation.OperationSource.PAYMENT,
                payment.getTelegramId(),
                payment.getId(),
                plan.getGenerationsCount(),
                plan.getTitle()
        );

        return operation;
    }

    @Transactional
    public GenerationBalanceOperation createNewBalanceOperationCredit(
            GenerationBalanceOperation.OperationSource source,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment
    ) {
        return createNewBalanceOperation(
                GenerationBalanceOperation.OperationType.CREDIT,
                source, telegramId, paymentId, requestCount, comment
        );
    }

    @Transactional
    public GenerationBalanceOperation createNewBalanceOperationDebit(
            GenerationBalanceOperation.OperationSource source,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment
    ) {
        return createNewBalanceOperation(
                GenerationBalanceOperation.OperationType.DEBIT,
                source, telegramId, paymentId, requestCount, comment
        );
    }

    @Transactional
    public GenerationBalanceOperation createNewBalanceOperation(
            GenerationBalanceOperation.OperationType operationType,
            GenerationBalanceOperation.OperationSource source,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment
    ) {
        GenerationBalance balance = generationBalanceService.getBalanceByTelegramIdOrNull(telegramId);

        switch (operationType) {
            case DEBIT -> {
                long current = balance.getGenerationRequests();
                if (current < requestCount) {
                    //TODO CUSTOM ERROR
                    throw new IllegalStateException(
                            "Not enough generation balance: have=" + current + ", need=" + requestCount
                    );
                }
                balance.subtract(requestCount);
            }
            case CREDIT -> balance.topUp(requestCount);
        }

        balance = generationBalanceService.save(balance);

        return repository.save(
                new GenerationBalanceOperation()
                        .setType(operationType)
                        .setSource(source)
                        .setTelegramId(telegramId)
                        .setPaymentId(paymentId)
                        .setComment(comment)
                        .setRequestCount(requestCount)
        );
    }
}
