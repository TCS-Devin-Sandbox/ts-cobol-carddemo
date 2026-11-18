package com.carddemo.trantype.web.dto;

import java.time.Instant;

public record LoginResponse(String token, String userId, String userType, Instant expiresAt) {
}
