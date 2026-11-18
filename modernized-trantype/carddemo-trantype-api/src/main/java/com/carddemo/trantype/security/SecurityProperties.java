package com.carddemo.trantype.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * @param jwtSecret   HMAC secret for HS256 tokens (at least 32 bytes)
 * @param tokenTtl    token lifetime
 * @param users       sign-on users (stand-in for USRSEC); type A = admin, U = regular user
 * @param corsOrigins allowed browser origins
 */
@ConfigurationProperties("carddemo.security")
public record SecurityProperties(String jwtSecret, Duration tokenTtl, List<User> users, List<String> corsOrigins) {

    public record User(String id, String password, String type) {
        public boolean isAdmin() {
            return "A".equalsIgnoreCase(type);
        }
    }
}
