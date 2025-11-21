package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.GenerationBalanceOperation;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.repositories.GenerationBalanceOperationRepository;
import com.kuklin.manageapp.bots.payment.services.exceptions.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.bots.payment.services.exceptions.PricingPlanNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис логирующий информацию об операциях, происходящий с балансом генераций пользователя
 *
 * Отвечает за:
 * - операции с балансом генераций пользователя
 * - сохранение информации об операциях с балансом генераций
 */
@Service
@RequiredArgsConstructor
public class GenerationBalanceOperationService {
    private final GenerationBalanceOperationRepository repository;
    private final GenerationBalanceService generationBalanceService;
    private final PricingPlanService pricingPlanService;

    //Пополнение баланса пользователя, на основании пришедшего платежа
    @Transactional
    public GenerationBalanceOperation increaseBalanceByPayment(Payment payment)
            throws GenerationBalanceNotFoundException, PricingPlanNotFoundException {

        PricingPlan plan = pricingPlanService
                .getPricingPlanById(payment.getPricingPlanId());

        GenerationBalanceOperation operation = createNewBalanceOperationCredit(
                GenerationBalanceOperation.OperationSource.PAYMENT,
                payment.getTelegramId(),
                payment.getId(),
                plan.getGenerationsCount(),
                plan.getTitle()
        );

        return operation;
    }

    //Пополнение баланса генераций пользователя
    @Transactional
    public GenerationBalanceOperation createNewBalanceOperationCredit(
            GenerationBalanceOperation.OperationSource source,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment
    ) throws GenerationBalanceNotFoundException {
        return createNewBalanceOperation(
                GenerationBalanceOperation.OperationType.CREDIT,
                source, telegramId, paymentId, requestCount, comment, false
        );
    }

    //Списание генераций из баланса пользователя
    @Transactional
    public GenerationBalanceOperation createNewBalanceOperationDebit(
            GenerationBalanceOperation.OperationSource source,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment,
            boolean isRefund
    ) throws GenerationBalanceNotFoundException {
        return createNewBalanceOperation(
                GenerationBalanceOperation.OperationType.DEBIT,
                source, telegramId, paymentId, requestCount, comment, isRefund
        );
    }

    //Общий метод проведения операций над балансом пользователя
    @Transactional
    public GenerationBalanceOperation createNewBalanceOperation(
            GenerationBalanceOperation.OperationType operationType,
            GenerationBalanceOperation.OperationSource source,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment,
            boolean isRefund
    ) throws GenerationBalanceNotFoundException {
        GenerationBalance balance = generationBalanceService.getBalanceByTelegramId(telegramId);

        switch (operationType) {
            case DEBIT -> {
                long current = balance.getGenerationRequests();
                if (current < requestCount) {

                    if (!isRefund) {
                        //TODO CUSTOM ERROR
                        throw new IllegalStateException(
                                "Not enough generation balance: have=" + current + ", need=" + requestCount
                        );
                    }
                }
                balance.subtract(requestCount);
            }
            case CREDIT -> balance.topUp(requestCount);
        }

        generationBalanceService.save(balance);

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
