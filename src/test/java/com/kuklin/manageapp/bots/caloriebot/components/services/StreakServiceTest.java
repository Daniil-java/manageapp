package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.models.report.StreakDto;
import com.kuklin.manageapp.bots.caloriebot.models.report.StreakDto.StreakDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    @Test
    void streakCountsUpToToday() {
        StreakDto streak = StreakService.buildStreak(days(0, 1, 2, 4), TODAY);

        assertThat(streak.current()).isEqualTo(3);
        assertThat(streak.todayLogged()).isTrue();
        assertThat(streak.best()).isEqualTo(3);
    }

    @Test
    void streakSurvivesUntilTodayEnds() {
        // Сегодня ещё пусто — серия от вчера, но «под угрозой»
        StreakDto streak = StreakService.buildStreak(days(1, 2), TODAY);

        assertThat(streak.current()).isEqualTo(2);
        assertThat(streak.todayLogged()).isFalse();
    }

    @Test
    void streakBreaksAfterMissedDay() {
        StreakDto streak = StreakService.buildStreak(days(2, 3), TODAY);

        assertThat(streak.current()).isZero();
        assertThat(streak.best()).isEqualTo(2);
    }

    @Test
    void bestStreakIsFromWholeHistory() {
        // Месяц назад — 5 дней подряд, сейчас — 2
        StreakDto streak = StreakService.buildStreak(days(0, 1, 30, 31, 32, 33, 34), TODAY);

        assertThat(streak.current()).isEqualTo(2);
        assertThat(streak.best()).isEqualTo(5);
    }

    @Test
    void last7EndsToday() {
        StreakDto streak = StreakService.buildStreak(days(0, 3, 6, 7), TODAY);

        assertThat(streak.last7()).extracting(StreakDay::date)
                .containsExactly(TODAY.minusDays(6), TODAY.minusDays(5), TODAY.minusDays(4),
                        TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
        assertThat(streak.last7()).extracting(StreakDay::logged)
                .containsExactly(true, false, false, true, false, false, true);
    }

    @Test
    void noRecords() {
        StreakDto streak = StreakService.buildStreak(Set.of(), TODAY);

        assertThat(streak.current()).isZero();
        assertThat(streak.best()).isZero();
        assertThat(streak.last7()).hasSize(7).noneMatch(StreakDay::logged);
    }

    private static Set<LocalDate> days(int... daysAgo) {
        return Arrays.stream(daysAgo).mapToObj(TODAY::minusDays).collect(Collectors.toSet());
    }
}
