package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.components.services.UtmReportService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.UtmStatsCalorieUpdateHandler.StatsRequest;
import com.kuklin.manageapp.common.entities.TelegramUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UtmStatsCalorieUpdateHandlerTest {

    private static final long ADMIN = 425120436L;

    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final UtmReportService reportService = mock(UtmReportService.class);
    private final UtmStatsCalorieUpdateHandler handler = new UtmStatsCalorieUpdateHandler(bot, reportService);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "adminTelegramIds", Set.of(ADMIN));
    }

    private static Update buildUpdate(String text) {
        Message message = new Message();
        message.setText(text);
        Chat chat = new Chat();
        chat.setId(ADMIN);
        message.setChat(chat);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    @Test
    void parsesSummaryCodeAndDaysInAnyOrder() {
        assertThat(UtmStatsCalorieUpdateHandler.parseRequest("/utmstats"))
                .isEqualTo(new StatsRequest(null, UtmReportService.DEFAULT_DAYS));
        assertThat(UtmStatsCalorieUpdateHandler.parseRequest("/utmstats 7")).isEqualTo(new StatsRequest(null, 7));
        assertThat(UtmStatsCalorieUpdateHandler.parseRequest("/utmstats blogger_oleg 14"))
                .isEqualTo(new StatsRequest("blogger_oleg", 14));
        assertThat(UtmStatsCalorieUpdateHandler.parseRequest("/utmstats 14 ref")).isEqualTo(new StatsRequest("ref", 14));
    }

    @Test
    void daysAreClamped() {
        assertThat(UtmStatsCalorieUpdateHandler.parseRequest("/utmstats 0").days()).isEqualTo(1);
        assertThat(UtmStatsCalorieUpdateHandler.parseRequest("/utmstats 999").days()).isEqualTo(UtmReportService.MAX_DAYS);
    }

    @Test
    void adminGetsSummary() {
        when(reportService.buildReport(30)).thenReturn("report");

        handler.handle(buildUpdate("/utmstats"), new TelegramUser().setTelegramId(ADMIN));

        verify(bot).sendReturnedMessage(ADMIN, "report");
    }

    @Test
    void adminGetsCodeReport() {
        when(reportService.buildCodeReport("blogger_oleg", 7)).thenReturn("code report");

        handler.handle(buildUpdate("/utmstats blogger_oleg 7"), new TelegramUser().setTelegramId(ADMIN));

        verify(bot).sendReturnedMessage(ADMIN, "code report");
    }

    @Test
    void nonAdminIsIgnored() {
        handler.handle(buildUpdate("/utmstats"), new TelegramUser().setTelegramId(555L));

        verifyNoInteractions(reportService, bot);
    }
}
