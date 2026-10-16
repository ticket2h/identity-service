package com.ticket2h.identity.dto.response;

/** {@code expiresIn}: số giây token còn hiệu lực. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
