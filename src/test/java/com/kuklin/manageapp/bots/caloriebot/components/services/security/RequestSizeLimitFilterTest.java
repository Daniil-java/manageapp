package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiInputLimitsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeLimitFilterTest {

    private RequestSizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        AiInputLimitsProperties limits = new AiInputLimitsProperties();
        limits.setRequestMaxSize(DataSize.ofBytes(10));
        filter = new RequestSizeLimitFilter(limits, new ObjectMapper());
    }

    @Test
    void bodyWithinLimitPassesUnchanged() throws Exception {
        MockHttpServletRequest request = post(new byte[10]);
        AtomicReference<ServletRequest> passed = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), chain(passed));

        assertThat(passed.get()).isSameAs(request);
    }

    @Test
    void contentLengthOverLimitGets413WithoutCallingChain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> passed = new AtomicReference<>();

        filter.doFilter(post(new byte[11]), response, chain(passed));

        assertThat(passed.get()).isNull();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
    }

    @Test
    void bodyWithoutContentLengthIsCutOffWhileReading() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/calorie/dishes/photo") {
            @Override
            public long getContentLengthLong() {
                return -1; // chunked
            }
        };
        request.setContent(new byte[11]);
        AtomicReference<ServletRequest> passed = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), chain(passed));

        InputStream body = passed.get().getInputStream();
        assertThatThrownBy(body::readAllBytes).isInstanceOf(RequestSizeLimitFilter.RequestTooLargeException.class);
    }

    private static MockHttpServletRequest post(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/calorie/dishes/photo");
        request.setContent(body);
        return request;
    }

    private static FilterChain chain(AtomicReference<ServletRequest> passed) {
        return (req, res) -> passed.set(req);
    }
}
