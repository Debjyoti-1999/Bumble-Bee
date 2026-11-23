package com.datingapp.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserProfileResponse user
) {
    public static AuthResponse bearer(String token, long expiresInSeconds, UserProfileResponse user) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, user);
    }
}
