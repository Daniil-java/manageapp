package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.common.auth.dto.AuthResponse;
import com.kuklin.manageapp.common.auth.dto.LoginRequest;
import com.kuklin.manageapp.common.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация и вход")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Регистрация нового пользователя")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        // @Valid — обязательно, иначе @NotBlank/@Email на RegisterRequest не работают
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, ClientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/login")
    @Operation(summary = "Вход по email + пароль")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, ClientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/telegram")
    @Operation(summary = "Вход через Telegram Login Widget",
            description = "Тело — объект user из data-onauth виджета как есть: id, first_name, last_name, "
                    + "username, photo_url, auth_date, hash. Возвращает тот же JWT, что и /auth/login.")
    public ResponseEntity<AuthResponse> loginByTelegram(@RequestBody Map<String, Object> widgetData,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.loginByTelegram(widgetData, ClientIpResolver.resolve(httpRequest)));
    }
}
