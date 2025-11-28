package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.GenerationBalanceOperation;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.repositories.GenerationBalanceOperationRepository;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceIllegalOperationDataException;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceNotEnoughBalanceException;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.bots.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис логирующий информацию об операциях, происходящий с балансом генераций пользователя
 * <p>
 * Отвечает за:
 * - операции с балансом генераций пользователя
 * - сохранение информации об операциях с балансом генераций
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationBalanceOperationService {
    private final GenerationBalanceOperationRepository repository;
    private final GenerationBalanceService generationBalanceService;
    private final PricingPlanService pricingPlanService;

    //Пополнение баланса пользователя, на основании пришедшего платежа
    @Transactional
    public GenerationBalanceOperation increaseBalanceByPayment(Payment payment)
            throws GenerationBalanceNotFoundException, PricingPlanNotFoundException, GenerationBalanceIllegalOperationDataException, GenerationBalanceNotEnoughBalanceException {

        PricingPlan plan = pricingPlanService
                .getPricingPlanById(payment.getPricingPlanId());

        GenerationBalanceOperation operation = createNewBalanceOperationCredit(
                GenerationBalanceOperation.OperationSource.PAYMENT,
                payment.getBotIdentifier(),
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
            BotIdentifier botIdentifier,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment
    ) throws GenerationBalanceNotFoundException, GenerationBalanceIllegalOperationDataException, GenerationBalanceNotEnoughBalanceException {
        return createNewBalanceOperation(
                GenerationBalanceOperation.OperationType.CREDIT,
                source, botIdentifier, telegramId, paymentId, requestCount, comment, false
        );
    }

    //Списание генераций из баланса пользователя
    @Transactional
    public GenerationBalanceOperation createNewBalanceOperationDebit(
            GenerationBalanceOperation.OperationSource source,
            BotIdentifier botIdentifier,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment,
            boolean isRefund
    ) throws GenerationBalanceNotFoundException, GenerationBalanceIllegalOperationDataException, GenerationBalanceNotEnoughBalanceException {
        return createNewBalanceOperation(
                GenerationBalanceOperation.OperationType.DEBIT,
                source, botIdentifier, telegramId, paymentId, requestCount, comment, isRefund
        );
    }

    //Общий метод проведения операций над балансом пользователя
    @Transactional
    public GenerationBalanceOperation createNewBalanceOperation(
            GenerationBalanceOperation.OperationType operationType,
            GenerationBalanceOperation.OperationSource source,
            BotIdentifier botIdentifier,
            Long telegramId,
            Long paymentId,
            Long requestCount,
            String comment,
            boolean isRefund
    ) throws GenerationBalanceNotFoundException, GenerationBalanceIllegalOperationDataException, GenerationBalanceNotEnoughBalanceException {
        GenerationBalance balance = generationBalanceService
                .getBalanceByTelegramIdAndBotIdentifier(telegramId, botIdentifier);

        if (requestCount < 0) {
            log.error(GenerationBalanceIllegalOperationDataException.DEF_MSG);
            throw new GenerationBalanceIllegalOperationDataException();
        }
        switch (operationType) {
            case DEBIT -> {
                long current = balance.getGenerationRequests();
                if (current < requestCount) {

                    //Если это не возврат, то счет пользователя не может опуститься ниже 0
                    if (!isRefund) {
                        log.error(GenerationBalanceNotEnoughBalanceException.DEF_MSG);
                        throw new GenerationBalanceNotEnoughBalanceException();
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
                        .setBotIdentifier(botIdentifier)
        );
    }
}
