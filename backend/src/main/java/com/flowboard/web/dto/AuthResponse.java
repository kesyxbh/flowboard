package com.flowboard.web.dto;

public record AuthResponse(String token, UserResponse user) {
}
