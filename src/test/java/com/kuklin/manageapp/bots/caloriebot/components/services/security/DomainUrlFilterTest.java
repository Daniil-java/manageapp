package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DomainUrlFilterTest {

    private final DomainUrlFilter filter = new DomainUrlFilter();

    @Test
    void oldCalorieLinkOnKuklinDevRedirectsToZefirWithQuery() throws Exception {
        MockHttpServletRequest request = request("GET", "kuklin.dev", "/calorie/privacy");
        request.setQueryString("lang=en");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(request, response, chain(passed));

        assertThat(passed).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("https://zefir.fit/calorie/privacy?lang=en");
    }

    @Test
    void nonGetToCalorieOnKuklinDevStays404() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("POST", "kuklin.dev", "/calorie/dishes"), response, chain(new AtomicBoolean()));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void privacyOnZefirPasses() throws Exception {
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(request("GET", "zefir.fit", "/calorie/privacy"), new MockHttpServletResponse(), chain(passed));

        assertThat(passed).isTrue();
    }

    private static MockHttpServletRequest request(String method, String host, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServerName(host);
        return request;
    }

    private static FilterChain chain(AtomicBoolean passed) {
        return (req, res) -> passed.set(true);
    }
}
