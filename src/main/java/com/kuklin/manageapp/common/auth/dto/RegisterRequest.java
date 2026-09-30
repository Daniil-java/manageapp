package com.kuklin.manageapp.common.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254)
        String email,
        // max 72 — BCrypt молча обрезает всё, что длиннее
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "должен содержать букву и цифру")
        String password,
        @Size(max = 64)
        String username,
        @Size(max = 64)
        String firstname,
        @Size(max = 64)
        String lastname
) {}
