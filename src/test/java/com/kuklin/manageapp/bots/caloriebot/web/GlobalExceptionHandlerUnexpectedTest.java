package com.kuklin.manageapp.bots.caloriebot.web;

import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.web.error.ErrorStatus;
import com.kuklin.manageapp.common.web.error.ErrorResponse;
import com.kuklin.manageapp.common.web.security.RequestSizeLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Непредусмотренная ошибка — 500 UNEXPECTED_ERROR в нашем формате; ошибки доступа — дальше, Spring Security.
 */
class GlobalExceptionHandlerUnexpectedTest {

    private final CalorieTexts texts = mock(CalorieTexts.class);
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(mock(RequestSizeLimitFilter.class), texts);
    private final ServletWebRequest request =
            new ServletWebRequest(new MockHttpServletRequest("GET", "/calorie/dishes"));

    @Test
    void unexpectedErrorBecomes500InOurFormat() throws Exception {
        when(texts.getApiMessage(any())).thenReturn("Something went wrong");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new NullPointerException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorStatus.UNEXPECTED_ERROR);
        // текст исключения клиенту не уходит
        assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong");
    }

    @Test
    void accessDeniedIsLeftToSpringSecurity() {
        AccessDeniedException denied = new AccessDeniedException("no");

        assertThatThrownBy(() -> handler.handleUnexpected(denied, request)).isSameAs(denied);
    }
}
