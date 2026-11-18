package com.carddemo.trantype.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void missingAdminPasswordFailsStartUp() {
        assertThatThrownBy(() -> config.userDetailsService(encoder, "admin", "", "user", "user123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CARDDEMO_ADMIN_PASSWORD");
    }

    @Test
    void missingUserPasswordFailsStartUp() {
        assertThatThrownBy(() -> config.userDetailsService(encoder, "admin", "secret", "user", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CARDDEMO_USER_PASSWORD");
    }

    @Test
    void configuredPasswordsCreateUsers() {
        assertThat(config.userDetailsService(encoder, "admin", "secret", "user", "other").loadUserByUsername("admin")
                .getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }
}
