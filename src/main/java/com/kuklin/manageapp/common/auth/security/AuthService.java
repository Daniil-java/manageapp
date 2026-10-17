package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.auth.dto.AuthResponse;
import com.kuklin.manageapp.common.auth.dto.LoginRequest;
import com.kuklin.manageapp.common.auth.dto.RegisterRequest;
import com.kuklin.manageapp.common.auth.security.JwtService;
import com.kuklin.manageapp.common.entities.AppUser;
import com.kuklin.manageapp.common.entities.Role;
import com.kuklin.manageapp.common.services.AppUserService;
import com.kuklin.manageapp.common.services.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserService appUserService;
    private final RoleService roleService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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

    public AuthResponse login(LoginRequest request) {
        // Одинаковое сообщение намеренно — не раскрываем, существует ли email
        AppUser user = appUserService.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Неверный email или пароль"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Неверный email или пароль");
        }

        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(AppUser user) {
        String token = jwtService.generateToken(user);

        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getRoleName().name())
                .collect(Collectors.toSet());

        return new AuthResponse(token, user.getId(), user.getEmail(), roles);
    }
}
