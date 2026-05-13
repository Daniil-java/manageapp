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

    private final Map<String, List<String>> allowedPathsByDomain = Map.of(
            "test1.com", List.of(
                    "/api/test1/**",
                    "/public/test1/**"
            ),
            "test2.com", List.of(
                    "/api/test2/**",
                    "/public/test2/**"
            )
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String host = request.getServerName().toLowerCase(Locale.ROOT);
        String path = request.getRequestURI();

        List<String> allowedPaths = allowedPathsByDomain.get(host);

        if (allowedPaths == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean allowed = allowedPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (!allowed) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
