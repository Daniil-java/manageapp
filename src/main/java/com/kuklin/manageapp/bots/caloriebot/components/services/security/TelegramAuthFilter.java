package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import com.kuklin.manageapp.common.entities.AppUser;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.services.AppUserService;
import com.kuklin.manageapp.common.services.TelegramUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramAuthFilter extends OncePerRequestFilter {

    // Сервис для валидации initData и извлечения данных пользователя
    private final TelegramAuthService telegramAuthService;
    private final TelegramUserService telegramUserService;
    private final AppUserService appUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String initData = request.getHeader("X-TG-INIT-DATA");

        if (initData != null && !initData.isBlank() && telegramAuthService.isValid(initData)) {
            // Достаем только ID
            Long telegramId = telegramAuthService.extractTelegramId(initData);

            if (telegramId != null) {
                // Берем ЛЮБУЮ запись с этим telegramId (ведь AppUser у них общий)
                TelegramUser tgUser = telegramUserService.findFirstByTelegramId(telegramId).orElse(null);

                if (tgUser != null && tgUser.getAppUserId() != null) {
                    // Подгружаем реальные роли AppUser'а из БД — так же, как JwtAuthenticationFilter
                    // достаёт их из JWT-клеймов. Если ролей нет (не должно случаться — ROLE_USER выдаётся
                    // при создании AppUser) — loadAuthorities() подстрахуется дефолтом.
                    List<GrantedAuthority> authorities = loadAuthorities(tgUser.getAppUserId());

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            tgUser.getAppUserId(),
                            null,
                            authorities
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> loadAuthorities(Long appUserId) {
        AppUser appUser = appUserService.getAppUserByIdOrNull(appUserId);

        if (appUser == null || appUser.getRoles().isEmpty()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return appUser.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.getRoleName().name()))
                .toList();
    }
}
