package com.kuklin.manageapp.bots.caloriebot.access;

import com.kuklin.manageapp.bots.caloriebot.access.model.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.access.model.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.access.model.QuotaCharge;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Порядок прокси на настоящем Spring: аспект квот — снаружи @Transactional.
 * Метод с обеими аннотациями — как UserFavoriteDishService.saveFromDish.
 */
@SpringJUnitConfig(FeatureAccessAspectOrderTest.Config.class)
class FeatureAccessAspectOrderTest {

    private static final Long USER_ID = 42L;
    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;
    private static final BotFeature FEATURE = BotFeature.DISH_FAVORITE_LIST;

    @Autowired private SampleService sampleService;
    // Моки — не бины: иначе Spring обернёт их @Transactional-методы в прокси
    private static final CalorieAccessService accessService = mock(CalorieAccessService.class);
    private static final UserFeatureUsageService usageService = mock(UserFeatureUsageService.class);
    @Autowired private FakeTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        reset(accessService, usageService);
        transactionManager.failCommit = false;
    }

    @Test
    void quotaIsChargedOutsideMethodTransaction() {
        AtomicReference<Boolean> txActiveWhenCharging = new AtomicReference<>();
        when(accessService.tryConsume(USER_ID, BOT, FEATURE)).thenAnswer(inv -> {
            txActiveWhenCharging.set(TransactionSynchronizationManager.isActualTransactionActive());
            return QuotaCharge.CHARGED;
        });

        sampleService.save(USER_ID);

        assertThat(txActiveWhenCharging.get()).isFalse();
        assertThat(SampleService.TX_ACTIVE_IN_METHOD.get()).isTrue();
    }

    @Test
    void failedCommitRefundsQuota() {
        when(accessService.tryConsume(USER_ID, BOT, FEATURE)).thenReturn(QuotaCharge.CHARGED);
        transactionManager.failCommit = true;

        assertThatThrownBy(() -> sampleService.save(USER_ID)).isInstanceOf(TransactionSystemException.class);

        verify(usageService).refundUsage(USER_ID, BOT, FEATURE);
    }

    static class SampleService {
        // static: поле экземпляра через CGLIB-прокси читалось бы у прокси, а не у объекта
        static final AtomicReference<Boolean> TX_ACTIVE_IN_METHOD = new AtomicReference<>();

        @Transactional
        @RequiresFeature(value = BotFeature.DISH_FAVORITE_LIST, botIdentifier = BotIdentifier.CALORIE_BOT)
        public AccessResult<String> save(Long userId) {
            TX_ACTIVE_IN_METHOD.set(TransactionSynchronizationManager.isActualTransactionActive());
            return AccessResult.success("saved");
        }
    }

    /** Менеджер транзакций без БД: только отмечает начало/коммит, коммит может упасть по флагу. */
    static class FakeTransactionManager extends AbstractPlatformTransactionManager {
        volatile boolean failCommit;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("commit failed");
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }

    @Configuration
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    static class Config {
        @Bean FakeTransactionManager transactionManager() { return new FakeTransactionManager(); }
        @Bean SampleService sampleService() { return new SampleService(); }

        @Bean
        FeatureAccessAspect featureAccessAspect() {
            return new FeatureAccessAspect(accessService, usageService);
        }
    }
}
