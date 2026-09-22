package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.common.auth.dto.AuthResponse;
import com.kuklin.manageapp.common.entities.AppUser;
import com.kuklin.manageapp.common.entities.Role;
import com.kuklin.manageapp.common.services.AppUserService;
import com.kuklin.manageapp.common.services.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ТОЛЬКО ДЛЯ ЛОКАЛЬНОЙ РАЗРАБОТКИ. Закрыть ROLE_ADMIN'ом перед продом.
 */
@RestController
@RequestMapping("/auth/dev")
@RequiredArgsConstructor
@Tag(name = "Dev", description = "Отладка авторизации (не для прода)")
public class DevAuthController {

    private final AppUserService appUserService;
    private final RoleService roleService;
    private final JwtService jwtService;

    @GetMapping("/whoami")
    @Operation(summary = "Что сейчас лежит в SecurityContext")
    public Object whoami() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "SecurityContext пуст";
        }

        return Map.of(
                "principal", auth.getPrincipal(),
                "authorities", auth.getAuthorities().toString(),
                "authClass", auth.getClass().getSimpleName()
        );
    }

    @PostMapping("/roles/{roleName}")
    @Operation(summary = "Выдать роль текущему пользователю и перевыпустить токен")
    public AuthResponse grantRole(@PathVariable Role.RoleName roleName) {
        Long appUserId = currentAppUserId();
        Role role = roleService.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Роль не найдена: " + roleName));
        AppUser user = appUserService.addRole(appUserId, role);
        return toResponse(user);
    }

    @DeleteMapping("/roles/{roleName}")
    @Operation(summary = "Забрать роль у текущего пользователя и перевыпустить токен")
    public AuthResponse revokeRole(@PathVariable Role.RoleName roleName) {
        Long appUserId = currentAppUserId();
        AppUser user = appUserService.removeRole(appUserId, roleName);
        return toResponse(user);
    }

    private Long currentAppUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private AuthResponse toResponse(AppUser user) {
        String token = jwtService.generateToken(user);
        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getRoleName().name())
                .collect(Collectors.toSet());
        return new AuthResponse(token, user.getId(), user.getEmail(), roles);
    }
}
