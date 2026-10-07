package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class DomainUrlFilter extends OncePerRequestFilter {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final Map<String, DomainRules> rulesByDomain = Map.of(
        "zefir.fit", new DomainRules(
            List.of("/", "/calorie/**", "/auth/**"),
            List.of()
        ),
        "kuklin.dev", new DomainRules(
            List.of("/**"),
            List.of("/calorie/**")),
        "manageapp-production.up.railway.app", new DomainRules(
            List.of("/nicotine/**"),
            List.of())
    );

    // Только для локальной разработки: пускать запросы на localhost/127.0.0.1 ко всем путям.
    // Host приходит от клиента, поэтому на проде флаг должен оставаться выключенным.
    private static final DomainRules LOCAL_RULES = new DomainRules(List.of("/**"), List.of());

    @Value("${security.domain-filter.allow-localhost:false}")
    private boolean allowLocalhost;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        String host = normalizeHost(request.getServerName());
        String path = request.getRequestURI();

        DomainRules rules = findRulesForHost(host);

        if (rules == null) {
            log.info("[DOMAIN_URL_FILTER] Host {}, path {}, rules not found", host, path);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean excluded = rules.excludedPaths().stream()
                                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (excluded) {
            log.info("[DOMAIN_URL_FILTER] Host {}, path {}, excluded", host, path);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean allowed = rules.allowedPaths().stream()
                               .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (!allowed) {
            log.info("[DOMAIN_URL_FILTER] Host {}, path {}, not allowed", host, path);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String normalizeHost(String host) {
        host = host.toLowerCase(Locale.ROOT);

        int colonIdx = host.indexOf(':');
        if (colonIdx != -1) {
            host = host.substring(0, colonIdx);
        }


        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }

        return host;
    }

    private DomainRules findRulesForHost(String host) {
        if (allowLocalhost && (host.equals("localhost") || host.equals("127.0.0.1"))) {
            return LOCAL_RULES;
        }
        return rulesByDomain.entrySet().stream()
                            .filter(entry -> matchesDomainOrSubdomain(host, entry.getKey()))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(null);
    }

    private boolean matchesDomainOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private record DomainRules(
        List<String> allowedPaths,
        List<String> excludedPaths) { }
}

