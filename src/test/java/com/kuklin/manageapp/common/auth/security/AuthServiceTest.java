package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.bots.caloriebot.components.services.security.TelegramAuthService;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.auth.dto.LoginRequest;
import com.kuklin.manageapp.common.auth.dto.RegisterRequest;
import com.kuklin.manageapp.common.configurations.AuthProperties;
import com.kuklin.manageapp.common.entities.AppUser;
import com.kuklin.manageapp.common.services.AppUserService;
import com.kuklin.manageapp.common.services.RoleService;
import com.kuklin.manageapp.common.services.UserAuthIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String IP = "1.2.3.4";
    private static final String EMAIL = "user@mail.com";

    private AppUserService appUserService;
    private PasswordEncoder passwordEncoder;
    private TelegramAuthService telegramAuthService;
    private AuthRateLimiter rateLimiter;
    private AuthProperties properties;
    private AuthService service;

    @BeforeEach
    void setUp() {
        appUserService = mock(AppUserService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        telegramAuthService = mock(TelegramAuthService.class);
        rateLimiter = mock(AuthRateLimiter.class);
        properties = new AuthProperties();
        service = new AuthService(appUserService, mock(RoleService.class), mock(JwtService.class), passwordEncoder,
                telegramAuthService, mock(UserAuthIdentityService.class), mock(SiteAccessService.class),
                rateLimiter, properties);
    }

    @Test
    void registerDisabledByFlag() {
        RegisterRequest request = new RegisterRequest(EMAIL, "password1", null, "Dan", null);

        assertThatThrownBy(() -> service.register(request, IP))
                .isInstanceOf(ErrorResponseException.class)
                .extracting(e -> ((ErrorResponseException) e).getErrorStatus())
                .isEqualTo(ErrorStatus.EMAIL_AUTH_DISABLED);
        verifyNoInteractions(appUserService, rateLimiter);
    }

    @Test
    void loginDisabledByFlag() {
        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "password1"), IP))
                .extracting(e -> ((ErrorResponseException) e).getErrorStatus())
                .isEqualTo(ErrorStatus.EMAIL_AUTH_DISABLED);
        verifyNoInteractions(appUserService, rateLimiter);
    }

    @Test
    void wrongPasswordIsRecordedForEmail() {
        properties.getEmailPassword().setEnabled(true);
        AppUser user = new AppUser().setEmail(EMAIL).setPasswordHash("hash");
        when(appUserService.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "wrong"), IP))
                .isInstanceOf(BadCredentialsException.class);

        verify(rateLimiter).acquireIpOrThrow(AuthRateLimiter.Action.LOGIN, IP);
        verify(rateLimiter).checkEmailNotLockedOrThrow(EMAIL);
        verify(rateLimiter).recordFailedPassword(EMAIL);
        verify(rateLimiter, never()).resetFailedPasswords(any());
    }

    @Test
    void unknownEmailCountsAsWrongPassword() {
        properties.getEmailPassword().setEnabled(true);
        when(appUserService.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "any"), IP))
                .isInstanceOf(BadCredentialsException.class);
        verify(rateLimiter).recordFailedPassword(EMAIL);
    }

    @Test
    void lockedEmailStopsBeforePasswordCheck() {
        properties.getEmailPassword().setEnabled(true);
        org.mockito.Mockito.doThrow(new ErrorResponseException(ErrorStatus.AUTH_RATE_LIMIT))
                .when(rateLimiter).checkEmailNotLockedOrThrow(EMAIL);

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "any"), IP))
                .isInstanceOf(ErrorResponseException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void successfulLoginResetsFailures() {
        properties.getEmailPassword().setEnabled(true);
        AppUser user = new AppUser().setEmail(EMAIL).setPasswordHash("hash");
        when(appUserService.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);

        assertThat(service.login(new LoginRequest(EMAIL, "right"), IP)).isNotNull();
        verify(rateLimiter).resetFailedPasswords(EMAIL);
    }

    @Test
    void telegramLoginIsRateLimitedBeforeSignatureCheck() {
        org.mockito.Mockito.doThrow(new ErrorResponseException(ErrorStatus.AUTH_RATE_LIMIT))
                .when(rateLimiter).acquireIpOrThrow(AuthRateLimiter.Action.TELEGRAM, IP);

        assertThatThrownBy(() -> service.loginByTelegram(Map.of("id", 1), IP))
                .isInstanceOf(ErrorResponseException.class);
        verifyNoInteractions(telegramAuthService);
    }
}
