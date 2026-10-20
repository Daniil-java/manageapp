package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.DishRepository;
import com.kuklin.manageapp.bots.caloriebot.components.repository.WaterEntryRepository;
import com.kuklin.manageapp.bots.caloriebot.models.report.StreakDto;
import com.kuklin.manageapp.bots.caloriebot.models.report.StreakDto.StreakDay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Серия дней подряд с записями. День засчитывается, если записана еда или вода:
 * в днях без еды (голодание по схеме) пользователь всё равно отмечает воду — серия не рвётся.
 */
@Service
@RequiredArgsConstructor
public class StreakService {
    private static final int LAST_DAYS = 7;

    private final DishRepository dishRepository;
    private final WaterEntryRepository waterEntryRepository;
    private final UserSettingsService userSettingsService;

    @Transactional(readOnly = true)
    public StreakDto getStreak(Long userId) {
        ZoneId zone = userSettingsService.getZoneId(userId);
        return buildStreak(findActiveDays(userId, zone), LocalDate.now(zone));
    }

    @Transactional(readOnly = true)
    public int getCurrentStreak(Long userId) {
        return getStreak(userId).current();
    }

    /** Сколько разных дней (в таймзоне пользователя) есть блюда за [from, to). Вода не считается. */
    @Transactional(readOnly = true)
    public long countDishDaysBetween(Long userId, Instant from, Instant to) {
        ZoneId zone = userSettingsService.getZoneId(userId);
        return dishRepository.countDishDaysByUserIdBetween(userId, getSqlZoneName(zone), getSqlOffsetSeconds(zone), from, to);
    }

    private Set<LocalDate> findActiveDays(Long userId, ZoneId zone) {
        Set<LocalDate> days = new HashSet<>(waterEntryRepository.findWaterDatesByUserId(userId));
        for (Integer epochDay : dishRepository.findDishEpochDaysByUserId(userId, getSqlZoneName(zone), getSqlOffsetSeconds(zone))) {
            days.add(LocalDate.ofEpochDay(epochDay));
        }
        return days;
    }

    // Фиксированное смещение (+03:00, GMT+3) — числом поверх UTC, зона с переходами — по имени IANA
    private static String getSqlZoneName(ZoneId zone) {
        return zone.normalized() instanceof ZoneOffset ? "UTC" : zone.getId();
    }

    private static int getSqlOffsetSeconds(ZoneId zone) {
        return zone.normalized() instanceof ZoneOffset offset ? offset.getTotalSeconds() : 0;
    }

    static StreakDto buildStreak(Set<LocalDate> activeDays, LocalDate today) {
        boolean todayLogged = activeDays.contains(today);

        int current = 0;
        LocalDate day = todayLogged ? today : today.minusDays(1);
        while (activeDays.contains(day)) {
            current++;
            day = day.minusDays(1);
        }

        List<StreakDay> last7 = new ArrayList<>(LAST_DAYS);
        for (int i = LAST_DAYS - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            last7.add(new StreakDay(date, activeDays.contains(date)));
        }

        return new StreakDto(current, Math.max(current, findBestStreak(activeDays)), todayLogged, last7);
    }

    private static int findBestStreak(Set<LocalDate> activeDays) {
        int best = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate day : new TreeSet<>(activeDays)) {
            run = prev != null && prev.plusDays(1).equals(day) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = day;
        }
        return best;
    }
}
