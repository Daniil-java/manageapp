package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramAuthFilter extends OncePerRequestFilter {

    // Сервис для валидации initData и извлечения данных пользователя
    private final TelegramAuthService telegramAuthService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String initData = request.getHeader("X-TG-INIT-DATA");

        if (initData != null && !initData.isBlank() && telegramAuthService.isValid(initData)) {
            // Достаем только ID
            Long telegramId = telegramAuthService.extractTelegramId(initData);

            if (telegramId != null) {
                // Кладем Long напрямую в качестве Principal
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        telegramId, // Теперь Principal — это Long
                        null,
                        Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
