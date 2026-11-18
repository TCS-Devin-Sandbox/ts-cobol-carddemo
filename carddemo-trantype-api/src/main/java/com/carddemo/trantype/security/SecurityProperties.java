package com.carddemo.trantype.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carddemo.security")
public record SecurityProperties(String jwtSecret, long tokenTtlMinutes, List<User> users) {

    public record User(String username, String password, List<String> roles) {
    }
}
