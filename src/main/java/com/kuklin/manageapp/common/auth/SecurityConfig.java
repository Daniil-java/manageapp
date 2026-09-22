package com.kuklin.manageapp.common.auth;

import com.kuklin.manageapp.bots.caloriebot.components.services.security.DomainUrlFilter;
import com.kuklin.manageapp.bots.caloriebot.components.services.security.TelegramAuthFilter;
import com.kuklin.manageapp.common.auth.security.CustomUserDetailsService;
import com.kuklin.manageapp.common.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final DomainUrlFilter domainUrlFilter;
    private final TelegramAuthFilter telegramAuthFilter;
    // TODO userDetailsService нигде фактически не используется: логин в AuthService идёт напрямую через
    //  PasswordEncoder.matches(...), минуя UserDetailsService/AuthenticationManager. Bean authenticationManager()
    //  ниже по той же причине мёртвый. Либо доводим до ума (DaoAuthenticationProvider с этим сервисом
    //  + AuthService через AuthenticationManager), либо убираем оба как неиспользуемые.
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Публичные пути
                        // TODO "/calorie/test" открывает тестовый эндпоинт (TestController) и должен быть
                        //  убран отсюда, когда его уберём/зашелвим перед прод-коммитом.
                        // Реальные публичные страницы сайта (kuklin.dev) и калорийного бота (zefir.fit) —
                        .requestMatchers(
                                "/auth/register", "/auth/login",
                                "/error", "/favicon.ico",
                                "/swagger-ui/**", "/v3/api-docs/**", "/calorie/test",
                                "/", "/resume", "/freelance", "/pomidorotimer", "/hhbot/skills",
                                "/calorie/instruction", "/calorie/privacy", "/calorie/terms",
                                "/calorie/utm-form", "/calorie/utm/ownertypes").permitAll()
                        // Тестовые html-страницы (static/auth/* и Thymeleaf-шаблоны из WebController) —
                        // временно оставлены в коде, но доступны только ROLE_ADMIN.
                        .requestMatchers(
                                "/auth", "/login", "/register", "/dashboard",
                                "/auth/*.html", "/auth/*.css").hasRole("ADMIN")
                        .requestMatchers("/auth/dev/whoami").authenticated()
                        .requestMatchers("/auth/dev/roles/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.error("[AUTH_ENTRY_POINT] {} {} -> {}", request.getMethod(), request.getRequestURI(), authException.toString());
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage());
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.error("[ACCESS_DENIED] {} {} -> {}", request.getMethod(), request.getRequestURI(), accessDeniedException.toString());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedException.getMessage());
                        })
                )

                // Порядок фильтров очень важен!
                .addFilterBefore(domainUrlFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(telegramAuthFilter, DomainUrlFilter.class)
                .addFilterAfter(jwtFilter, TelegramAuthFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("x-tg-init-data", "X-TG-INIT-DATA", "Content-Type", "Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<DomainUrlFilter> domainUrlFilterRegistration(DomainUrlFilter filter) {
        FilterRegistrationBean<DomainUrlFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<TelegramAuthFilter> telegramAuthFilterRegistration(TelegramAuthFilter filter) {
        FilterRegistrationBean<TelegramAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
