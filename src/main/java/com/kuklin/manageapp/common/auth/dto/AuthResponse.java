package com.kuklin.manageapp.common.auth.dto;

import java.util.Set;

public record AuthResponse(
        String token,
        Long appUserId,
        String email,
        Set<String> roles
) {}
