package com.kuklin.manageapp.bots.metrics.telegram.handlers;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsLogUpdateHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void onTimeReportIsForToday() {
        assertThat(MetricsLogUpdateHandler.reportDay(ZonedDateTime.of(2026, 10, 8, 23, 50, 0, 0, ZONE)))
                .isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void lateReportAfterMidnightIsForYesterday() {
        // 08.10: отчёт ушёл в 00:21 и показал нули за 09.10
        assertThat(MetricsLogUpdateHandler.reportDay(ZonedDateTime.of(2026, 10, 9, 0, 21, 0, 0, ZONE)))
                .isEqualTo(LocalDate.of(2026, 10, 8));
    }
}
