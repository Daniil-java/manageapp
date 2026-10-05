package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.auth.dto.AuthResponse;
import com.kuklin.manageapp.common.auth.dto.LoginRequest;
import com.kuklin.manageapp.common.auth.dto.RegisterRequest;
import com.kuklin.manageapp.common.auth.security.JwtService;
import com.kuklin.manageapp.common.configurations.AuthProperties;
import com.kuklin.manageapp.common.entities.AppUser;
import com.kuklin.manageapp.common.entities.Role;
import com.kuklin.manageapp.common.services.AppUserService;
import com.kuklin.manageapp.common.services.RoleService;
import com.kuklin.manageapp.common.services.UserAuthIdentityService;
import com.kuklin.manageapp.bots.caloriebot.components.services.security.TelegramAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserService appUserService;
    private final RoleService roleService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TelegramAuthService telegramAuthService;
    private final UserAuthIdentityService userAuthIdentityService;
    private final SiteAccessService siteAccessService;
    private final AuthRateLimiter authRateLimiter;
    private final AuthProperties authProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        checkEmailPasswordEnabled();
        authRateLimiter.acquireIpOrThrow(AuthRateLimiter.Action.REGISTER, clientIp);
        siteAccessService.checkEmail(request.email());

        if (appUserService.existsByEmail(request.email())) {
            throw new ErrorResponseException(ErrorStatus.EMAIL_ALREADY_BUSY);
        }

        Role userRole = roleService.findByRoleName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException(
                        "Роль ROLE_USER не найдена в БД — проверь Liquibase-миграции"));

        AppUser user = new AppUser()
                .setEmail(request.email())
                .setPasswordHash(passwordEncoder.encode(request.password()))
                .setUsername(request.username())
                .setFirstname(request.firstname())
                .setLastname(request.lastname());

        user.addRole(userRole);
        user = appUserService.createUser(user);

        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        checkEmailPasswordEnabled();
        authRateLimiter.acquireIpOrThrow(AuthRateLimiter.Action.LOGIN, clientIp);
        siteAccessService.checkEmail(request.email());
        authRateLimiter.checkEmailNotLockedOrThrow(request.email());

        // Одинаковое сообщение намеренно — не раскрываем, существует ли email.
        // Несуществующий email тоже считается неверным паролем — по той же причине
        AppUser user = appUserService.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            authRateLimiter.recordFailedPassword(request.email());
            throw new BadCredentialsException("Неверный email или пароль");
        }

        authRateLimiter.resetFailedPasswords(request.email());
        return toAuthResponse(user);
    }

    /**
     * Вход на сайте через Telegram Login Widget: проверяем подпись, находим аккаунт по telegramId
     * (боты, мини-аппа или прошлый вход на сайте) или создаём новый — только AppUser + identity,
     * без TelegramUser: человек мог ни разу не открывать бота.
     */
    @Transactional
    public AuthResponse loginByTelegram(Map<String, Object> widgetData, String clientIp) {
        authRateLimiter.acquireIpOrThrow(AuthRateLimiter.Action.TELEGRAM, clientIp);

        // Подпись считается по всем присланным полям, поэтому берём их как есть, без DTO
        Map<String, String> params = new HashMap<>();
        widgetData.forEach((key, value) -> {
            if (value != null) params.put(key, String.valueOf(value));
        });

        if (!telegramAuthService.isValidLoginWidget(params)) {
            throw new BadCredentialsException("Неверные данные авторизации Telegram");
        }

        Long telegramId = Long.parseLong(params.get("id"));
        // Только после проверки подписи — иначе id можно подставить любой
        siteAccessService.checkTelegramId(telegramId);
        Long appUserId = userAuthIdentityService.findAppUserIdByTelegramId(telegramId)
                .orElseGet(() -> {
                    AppUser newUser = appUserService.createUser(new AppUser()
                            .setUsername(params.get("username"))
                            .setFirstname(params.get("first_name"))
                            .setLastname(params.get("last_name")));
                    userAuthIdentityService.linkTelegram(newUser, telegramId);
                    return newUser.getId();
                });

        AppUser user = appUserService.getAppUserByIdOrNull(appUserId);
        // Имя в Telegram могли сменить, а у аккаунтов из бота его могло не быть вовсе —
        // сайт показывает именно его, поэтому освежаем при каждом входе
        if (params.get("first_name") != null) {
            user.setUsername(params.get("username"))
                    .setFirstname(params.get("first_name"))
                    .setLastname(params.get("last_name"));
        }
        return toAuthResponse(user);
    }

    /**
     * Регистрация и вход по email + паролю выключены флагом auth.email-password.enabled,
     * пока нет подтверждения email (код оставлен — включить обратно одной переменной окружения).
     */
    private void checkEmailPasswordEnabled() {
        if (!authProperties.getEmailPassword().isEnabled()) {
            throw new ErrorResponseException(ErrorStatus.EMAIL_AUTH_DISABLED);
        }
    }

    private AuthResponse toAuthResponse(AppUser user) {
        return AuthResponse.of(jwtService.generateToken(user), user);
    }
}
