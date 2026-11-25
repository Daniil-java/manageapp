package com.kuklin.manageapp.bots.caloriebot.access;

import com.kuklin.manageapp.bots.caloriebot.access.model.BotFeature;
import com.kuklin.manageapp.common.PostgresRepositoryTest;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Квоты: строка счётчика создаётся без дублей, списание не проходит лимит, возврат не уходит ниже нуля.
 */
class UserFeatureUsageRepositoryTest extends PostgresRepositoryTest {

    private static final Long USER_ID = 42L;
    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;
    private static final BotFeature FEATURE = BotFeature.DISH_AI_VISION;

    @Autowired
    private UserFeatureUsageRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void insertIfAbsentCreatesRowOnce() {
        assertThat(insertRow()).isEqualTo(1);
        // второй (параллельный) первый запрос — без ошибки uk_usage_row и без дубля
        assertThat(insertRow()).isEqualTo(0);

        assertThat(repository.findAllByUserIdAndBotIdentifier(USER_ID, BOT)).hasSize(1);
    }

    @Test
    void tryIncrementUsageStopsAtLimit() {
        insertRow();

        assertThat(repository.tryIncrementUsage(USER_ID, BOT, FEATURE, 2)).isEqualTo(1);
        assertThat(repository.tryIncrementUsage(USER_ID, BOT, FEATURE, 2)).isEqualTo(1);
        assertThat(repository.tryIncrementUsage(USER_ID, BOT, FEATURE, 2)).isEqualTo(0);

        assertThat(getUsedCount()).isEqualTo(2);
    }

    @Test
    void tryIncrementUsageWithoutRowChargesNothing() {
        assertThat(repository.tryIncrementUsage(USER_ID, BOT, FEATURE, 5)).isEqualTo(0);
    }

    @Test
    void decrementUsageDoesNotGoBelowZero() {
        insertRow();
        repository.tryIncrementUsage(USER_ID, BOT, FEATURE, 5);

        assertThat(repository.decrementUsage(USER_ID, BOT, FEATURE)).isEqualTo(1);
        assertThat(repository.decrementUsage(USER_ID, BOT, FEATURE)).isEqualTo(0);

        assertThat(getUsedCount()).isZero();
    }

    private int insertRow() {
        return repository.insertIfAbsent(USER_ID, BOT.name(), FEATURE.name(),
                LocalDate.of(2026, 11, 23), LocalDateTime.of(2026, 11, 22, 21, 0));
    }

    private int getUsedCount() {
        // update в JPQL идёт мимо кеша сессии — сбрасываем его и читаем из БД
        entityManager.clear();
        return repository.findByUserIdAndFeatureAndAndBotIdentifier(USER_ID, FEATURE, BOT)
                .orElseThrow()
                .getUsedCount();
    }
}
