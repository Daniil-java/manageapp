package com.kuklin.manageapp.common.web.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    void oldLandingLinkOnKuklinDevRedirectsToZefirRoot() throws Exception {
        MockHttpServletRequest request = request("GET", "kuklin.dev", "/calorie");
        request.setQueryString("utm=abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain(new AtomicBoolean()));

        assertThat(response.getRedirectedUrl()).isEqualTo("https://zefir.fit/?utm=abc");
    }

    // У лендинга один адрес — корень zefir.fit
    @ParameterizedTest
    @CsvSource({
        "zefir.fit, /calorie, lang=en, https://zefir.fit/?lang=en",
        "zefir.fit, /calorie/, , https://zefir.fit/"
    })
    void landingDuplicateRedirectsPermanently(String host, String path, String query, String target)
            throws Exception {
        MockHttpServletRequest request = request("GET", host, path);
        request.setQueryString(query);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(request, response, chain(passed));

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(301);
        assertThat(response.getHeader("Location")).isEqualTo(target);
    }

    // Корень и остальные страницы лендинга не перенаправляются
    @ParameterizedTest
    @ValueSource(strings = {"/", "/calorie/privacy", "/calorie/terms"})
    void otherZefirPagesPass(String path) throws Exception {
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(request("GET", "zefir.fit", path), new MockHttpServletResponse(), chain(passed));

        assertThat(passed).isTrue();
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

    @ParameterizedTest
    @ValueSource(strings = {"/resume", "/freelance"})
    void personalPagesOnKuklinDevPass(String path) throws Exception {
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(request("GET", "kuklin.dev", path), new MockHttpServletResponse(), chain(passed));

        assertThat(passed).isTrue();
    }

    // Резюме и фриланс-страница — только на kuklin.dev
    @ParameterizedTest
    @CsvSource({
        "zefir.fit, /resume",
        "zefir.fit, /freelance",
        "manageapp-production.up.railway.app, /resume",
        "manageapp-production.up.railway.app, /freelance",
        "example.com, /resume"
    })
    void personalPagesOnOtherHostsAre404(String host, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();

        filter.doFilter(request("GET", host, path), response, chain(passed));

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(404);
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
