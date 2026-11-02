package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.UserFeatureUsage;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UserFeatureUsageServiceResetTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    /** Счётчик, сброшенный в день day по зоне zone — так, как его теперь записывает сервис. */
    private static UserFeatureUsage resetOn(LocalDate day, ZoneId zone) {
        return new UserFeatureUsage()
                .setLastResetLocalDate(day)
                .setLastResetUtc(LocalDateTime.ofInstant(day.atStartOfDay(zone).toInstant(), ZoneOffset.UTC));
    }

    private static Instant at(String localDateTime, ZoneId zone) {
        return LocalDateTime.parse(localDateTime).atZone(zone).toInstant();
    }

    @Test
    void dailyLimitResetsNextMorningAfterLateFirstUse() {
        // Первое фото 13.10 в 23:00 — счётчик сброшен в этот день. Раньше 14.10 в 10:00 прошло бы 11 часов
        // от момента сброса и лимит остался бы вчерашним до 17:00
        UserFeatureUsage usage = resetOn(LocalDate.of(2026, 10, 13), MOSCOW);

        assertThat(UserFeatureUsageService.shouldReset(usage, FeatureLimitPeriod.DAILY, MOSCOW,
                at("2026-10-14T10:00", MOSCOW))).isTrue();
    }

    @Test
    void dailyLimitResetsRightAfterMidnight() {
        UserFeatureUsage usage = resetOn(LocalDate.of(2026, 10, 13), MOSCOW);

        assertThat(UserFeatureUsageService.shouldReset(usage, FeatureLimitPeriod.DAILY, MOSCOW,
                at("2026-10-14T00:01", MOSCOW))).isTrue();
    }

    @Test
    void dailyLimitNotResetSameDay() {
        UserFeatureUsage usage = resetOn(LocalDate.of(2026, 10, 14), MOSCOW);

        assertThat(UserFeatureUsageService.shouldReset(usage, FeatureLimitPeriod.DAILY, MOSCOW,
                at("2026-10-14T23:59", MOSCOW))).isFalse();
    }

    @Test
    void switchingTimeZoneForwardDoesNotResetEarly() {
        // Сброс 14.10 по UTC-12, в 08:00 там же пользователь переключается на UTC+12: по календарю уже 15.10,
        // но с полуночи 14.10 по UTC-12 прошло 8 часов — не сбрасываем
        ZoneId minus12 = ZoneId.of("Etc/GMT+12");
        ZoneId plus12 = ZoneId.of("Etc/GMT-12");
        UserFeatureUsage usage = resetOn(LocalDate.of(2026, 10, 14), minus12);

        assertThat(UserFeatureUsageService.shouldReset(usage, FeatureLimitPeriod.DAILY, plus12,
                at("2026-10-14T08:00", minus12))).isFalse();
    }
}
