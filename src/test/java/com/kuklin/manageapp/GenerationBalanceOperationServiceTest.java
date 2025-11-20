package com.kuklin.manageapp;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.GenerationBalanceOperation;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.repositories.GenerationBalanceOperationRepository;
import com.kuklin.manageapp.bots.payment.services.GenerationBalanceOperationService;
import com.kuklin.manageapp.bots.payment.services.GenerationBalanceService;
import com.kuklin.manageapp.bots.payment.services.PricingPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerationBalanceOperationServiceTest {

    @Mock
    private GenerationBalanceOperationRepository operationRepository;

    @Mock
    private GenerationBalanceService generationBalanceService;

    @Mock
    private PricingPlanService pricingPlanService;

    @InjectMocks
    private GenerationBalanceOperationService service;

    @Test
    @DisplayName("CREDIT: баланс увеличивается и операция сохраняется")
    void createNewBalanceOperationCredit_increasesBalanceAndPersistsOperation() {
        // given
        Long telegramId = 123L;
        Long paymentId = 10L;
        Long requestCount = 5L;
        String comment = "Test credit";

        GenerationBalance balance = new GenerationBalance()
                .setId(1L)
                .setTelegramId(telegramId)
                .setGenerationRequests(10L);

        when(generationBalanceService.getBalanceByTelegramIdOrNull(telegramId))
                .thenReturn(balance);

        // имитируем, что save возвращает тот же объект
        when(generationBalanceService.save(any(GenerationBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // имитируем сохранение операции
        when(operationRepository.save(any(GenerationBalanceOperation.class)))
                .thenAnswer(invocation -> {
                    GenerationBalanceOperation op = invocation.getArgument(0);
                    op.setId(100L);
                    return op;
                });

        // when
        GenerationBalanceOperation result = service.createNewBalanceOperationCredit(
                GenerationBalanceOperation.OperationSource.PAYMENT,
                telegramId,
                paymentId,
                requestCount,
                comment
        );

        // then
        // 1) баланс увеличился и ушёл в save
        ArgumentCaptor<GenerationBalance> balanceCaptor =
                ArgumentCaptor.forClass(GenerationBalance.class);
        verify(generationBalanceService, times(1)).save(balanceCaptor.capture());

        GenerationBalance savedBalance = balanceCaptor.getValue();
        assertThat(savedBalance.getGenerationRequests())
                .as("generationRequests after CREDIT")
                .isEqualTo(10L + requestCount);

        // 2) операция ушла в репозиторий
        ArgumentCaptor<GenerationBalanceOperation> opCaptor =
                ArgumentCaptor.forClass(GenerationBalanceOperation.class);
        verify(operationRepository, times(1)).save(opCaptor.capture());

        GenerationBalanceOperation savedOp = opCaptor.getValue();
        assertThat(savedOp.getType()).isEqualTo(GenerationBalanceOperation.OperationType.CREDIT);
        assertThat(savedOp.getSource()).isEqualTo(GenerationBalanceOperation.OperationSource.PAYMENT);
        assertThat(savedOp.getTelegramId()).isEqualTo(telegramId);
        assertThat(savedOp.getPaymentId()).isEqualTo(paymentId);
        assertThat(savedOp.getComment()).isEqualTo(comment);
        assertThat(savedOp.getRequestCount()).isEqualTo(requestCount);

        // 3) результат — это то, что вернул репозиторий
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("DEBIT: при достаточном балансе — уменьшаем баланс и сохраняем операцию")
    void createNewBalanceOperationDebit_decreasesBalanceAndPersistsOperation() {
        // given
        Long telegramId = 123L;
        Long paymentId = 11L;
        Long requestCount = 3L;
        String comment = "Test debit";

        GenerationBalance balance = new GenerationBalance()
                .setId(1L)
                .setTelegramId(telegramId)
                .setGenerationRequests(10L);

        when(generationBalanceService.getBalanceByTelegramIdOrNull(telegramId))
                .thenReturn(balance);

        when(generationBalanceService.save(any(GenerationBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(operationRepository.save(any(GenerationBalanceOperation.class)))
                .thenAnswer(invocation -> {
                    GenerationBalanceOperation op = invocation.getArgument(0);
                    op.setId(200L);
                    return op;
                });

        // when
        GenerationBalanceOperation result = service.createNewBalanceOperationDebit(
                GenerationBalanceOperation.OperationSource.GENERATION,
                telegramId,
                paymentId,
                requestCount,
                comment,
                false
        );

        // then
        // 1) баланс уменьшился
        ArgumentCaptor<GenerationBalance> balanceCaptor =
                ArgumentCaptor.forClass(GenerationBalance.class);
        verify(generationBalanceService, times(1)).save(balanceCaptor.capture());

        GenerationBalance savedBalance = balanceCaptor.getValue();
        assertThat(savedBalance.getGenerationRequests())
                .as("generationRequests after DEBIT")
                .isEqualTo(10L - requestCount);

        // 2) операция сохранена
        ArgumentCaptor<GenerationBalanceOperation> opCaptor =
                ArgumentCaptor.forClass(GenerationBalanceOperation.class);
        verify(operationRepository, times(1)).save(opCaptor.capture());

        GenerationBalanceOperation savedOp = opCaptor.getValue();
        assertThat(savedOp.getType()).isEqualTo(GenerationBalanceOperation.OperationType.DEBIT);
        assertThat(savedOp.getSource()).isEqualTo(GenerationBalanceOperation.OperationSource.GENERATION);
        assertThat(savedOp.getTelegramId()).isEqualTo(telegramId);
        assertThat(savedOp.getPaymentId()).isEqualTo(paymentId);
        assertThat(savedOp.getComment()).isEqualTo(comment);
        assertThat(savedOp.getRequestCount()).isEqualTo(requestCount);

        assertThat(result.getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("DEBIT: при недостаточном балансе бросается исключение и в БД ничего не пишется")
    void createNewBalanceOperationDebit_notEnoughBalance_throwsAndDoesNotPersist() {
        // given
        Long telegramId = 123L;
        Long paymentId = 11L;
        Long requestCount = 5L;

        GenerationBalance balance = new GenerationBalance()
                .setId(1L)
                .setTelegramId(telegramId)
                .setGenerationRequests(2L); // < requestCount

        when(generationBalanceService.getBalanceByTelegramIdOrNull(telegramId))
                .thenReturn(balance);

        // when / then
        assertThatThrownBy(() -> service.createNewBalanceOperationDebit(
                GenerationBalanceOperation.OperationSource.GENERATION,
                telegramId,
                paymentId,
                requestCount,
                "Should fail",
                false
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not enough generation balance");

        // баланс не сохранялся
        verify(generationBalanceService, never()).save(any(GenerationBalance.class));
        // операция не сохранялась
        verify(operationRepository, never()).save(any(GenerationBalanceOperation.class));
    }

    @Test
    @DisplayName("increaseBalanceByPayment: дергает баланс, тариф и создает CREDIT-операцию")
    void increaseBalanceByPayment_createsCreditOperationBasedOnPaymentAndPlan() {
        // given
        Long telegramId = 123L;
        Long paymentId = 50L;
        Long pricingPlanId = 777L;

        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setTelegramId(telegramId);
        payment.setPricingPlanId(pricingPlanId);

        GenerationBalance balance = new GenerationBalance()
                .setId(1L)
                .setTelegramId(telegramId)
                .setGenerationRequests(0L);

        when(generationBalanceService.getBalanceByTelegramIdOrNull(telegramId))
                .thenReturn(balance);

        PricingPlan plan = new PricingPlan();
        plan.setId(pricingPlanId);
        plan.setGenerationsCount(10L);
        plan.setTitle("Test plan");

        when(pricingPlanService.getPricingPlanByIdOrNull(pricingPlanId))
                .thenReturn(plan);

        when(generationBalanceService.save(any(GenerationBalance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(operationRepository.save(any(GenerationBalanceOperation.class)))
                .thenAnswer(invocation -> {
                    GenerationBalanceOperation op = invocation.getArgument(0);
                    op.setId(300L);
                    return op;
                });

        // when
        GenerationBalanceOperation result = service.increaseBalanceByPayment(payment);

        // then
        // баланс должен увеличиться до 10
        ArgumentCaptor<GenerationBalance> balanceCaptor =
                ArgumentCaptor.forClass(GenerationBalance.class);
        verify(generationBalanceService).save(balanceCaptor.capture());
        assertThat(balanceCaptor.getValue().getGenerationRequests())
                .isEqualTo(10L);

        // операция сохраняется через репозиторий
        ArgumentCaptor<GenerationBalanceOperation> opCaptor =
                ArgumentCaptor.forClass(GenerationBalanceOperation.class);
        verify(operationRepository).save(opCaptor.capture());

        GenerationBalanceOperation savedOp = opCaptor.getValue();
        assertThat(savedOp.getType()).isEqualTo(GenerationBalanceOperation.OperationType.CREDIT);
        assertThat(savedOp.getSource()).isEqualTo(GenerationBalanceOperation.OperationSource.PAYMENT);
        assertThat(savedOp.getTelegramId()).isEqualTo(telegramId);
        assertThat(savedOp.getPaymentId()).isEqualTo(paymentId);
        assertThat(savedOp.getRequestCount()).isEqualTo(10L);
        assertThat(savedOp.getComment()).isEqualTo("Test plan");

        assertThat(result.getId()).isEqualTo(300L);
    }
}
