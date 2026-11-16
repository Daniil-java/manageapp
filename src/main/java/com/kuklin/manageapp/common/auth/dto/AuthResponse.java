package com.kuklin.manageapp.common.auth.dto;

import com.kuklin.manageapp.common.entities.AppUser;

import java.util.Set;
import java.util.stream.Collectors;

public record AuthResponse(
        String token,
        Long appUserId,
        String email,
        Set<String> roles,
        String username,
        String firstname
) {
    public static AuthResponse of(String token, AppUser user) {
        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getRoleName().name())
                .collect(Collectors.toSet());
        return new AuthResponse(token, user.getId(), user.getEmail(), roles,
                user.getUsername(), user.getFirstname());
    }
}
