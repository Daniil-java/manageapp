package com.kuklin.manageapp.bots.deparrbot.controllers;

import com.kuklin.manageapp.bots.deparrbot.models.AviaFlightResponse;
import com.kuklin.manageapp.bots.deparrbot.models.FlightCode;
import com.kuklin.manageapp.bots.deparrbot.services.AviaApiTokenService;
import com.kuklin.manageapp.bots.deparrbot.services.AviaWidgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AviaWidgetControllerTest {
    private static final String TOKEN = "good-token";
    private static final long TELEGRAM_ID = 42L;

    private final AviaApiTokenService tokenService = mock(AviaApiTokenService.class);
    private final AviaWidgetService widgetService = mock(AviaWidgetService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Mockito по умолчанию вернул бы 0L для Long — сервис для чужого токена возвращает null
        when(tokenService.findTelegramIdOrNull(any())).thenReturn(null);
        when(tokenService.findTelegramIdOrNull(TOKEN)).thenReturn(TELEGRAM_ID);
        mvc = MockMvcBuilders.standaloneSetup(new AviaWidgetController(tokenService, widgetService)).build();
    }

    private static AviaFlightResponse buildResponse() {
        return new AviaFlightResponse("AK880", "AirAsia", "KUL Kuala Lumpur", "DMK Bangkok",
                "25.11 08:00", "25.11 08:40", 40L, "25.11 09:20", "25.11 09:55", "В пути", "2", "Q15");
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/avia/subscriptions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/avia/subscriptions").header(AviaWidgetController.TOKEN_HEADER, "bad"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(widgetService);
    }

    @Test
    void returnsSubscriptions() throws Exception {
        when(widgetService.getSubscriptions(TELEGRAM_ID)).thenReturn(List.of(buildResponse()));

        mvc.perform(get("/avia/subscriptions").header(AviaWidgetController.TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flight").value("AK880"))
                .andExpect(jsonPath("$[0].gate").value("Q15"))
                .andExpect(jsonPath("$[0].departureDelayMinutes").value(40));
    }

    @Test
    void badFlightCodeIsBadRequest() throws Exception {
        mvc.perform(get("/avia/flights/ZZZ").header(AviaWidgetController.TOKEN_HEADER, TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownFlightIsNotFound() throws Exception {
        when(widgetService.findFlightOrNull(any())).thenReturn(null);

        mvc.perform(get("/avia/flights/AK999").header(AviaWidgetController.TOKEN_HEADER, TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void subscribeNormalizesCode() throws Exception {
        when(widgetService.subscribeOrNull(TELEGRAM_ID, new FlightCode("AK", "880"))).thenReturn(buildResponse());

        mvc.perform(post("/avia/subscriptions/ak0880").header(AviaWidgetController.TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flight").value("AK880"));
    }

    @Test
    void unsubscribeReturnsNoContent() throws Exception {
        mvc.perform(delete("/avia/subscriptions/AK880").header(AviaWidgetController.TOKEN_HEADER, TOKEN))
                .andExpect(status().isNoContent());
        verify(widgetService).unsubscribe(TELEGRAM_ID, new FlightCode("AK", "880"));
    }
}
