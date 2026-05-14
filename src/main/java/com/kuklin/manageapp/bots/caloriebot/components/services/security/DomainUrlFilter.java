package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DomainUrlFilter extends OncePerRequestFilter {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final Map<String, DomainRules> rulesByDomain = Map.of(
        "zefir.fit", new DomainRules(
            List.of("/calorie/**"),
            List.of()
        ),
        "kuklin.dev", new DomainRules(
            List.of("/**"),
            List.of("/calorie/**")),
        "railway.app", new DomainRules(
            List.of("/nicotine"),
            List.of())
                                                                 );

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        String host = normalizeHost(request.getServerName());
        String path = request.getRequestURI();

        DomainRules rules = findRulesForHost(host);

        if (rules == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean excluded = rules.excludedPaths().stream()
                                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (excluded) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean allowed = rules.allowedPaths().stream()
                               .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (!allowed) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String normalizeHost(String host) {
        host = host.toLowerCase(Locale.ROOT);

        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }

        return host;
    }

    private DomainRules findRulesForHost(String host) {
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

