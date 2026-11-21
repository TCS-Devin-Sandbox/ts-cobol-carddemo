package com.carddemo.signon;

import com.carddemo.trantype.security.SecurityProperties;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserSecurity implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SecurityProperties properties;

    public UserSecurity(JdbcTemplate jdbc, PasswordEncoder encoder, SecurityProperties properties) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.properties = properties;
    }

    public record User(String userId, String passwordHash, String userType) { }

    public Optional<User> find(String id) {
        return jdbc.query("SELECT user_id, password_hash, user_type FROM user_security WHERE user_id = ?",
                (rs, row) -> new User(rs.getString(1).stripTrailing(), rs.getString(2), rs.getString(3)), id)
                .stream().findFirst();
    }

    public boolean matches(String password, User user) {
        return encoder.matches(password.stripTrailing().toUpperCase(Locale.ROOT), user.passwordHash());
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.users().forEach(user -> {
            validateWidth("user ID", user.username());
            validateWidth("password", user.password());
        });
        properties.users().forEach(user -> jdbc.update("""
                INSERT INTO user_security(user_id, first_name, last_name, password_hash, user_type, filler)
                VALUES (?, '', '', ?, ?, '') ON CONFLICT (user_id) DO NOTHING
                """, user.username().stripTrailing().toUpperCase(Locale.ROOT),
                encoder.encode(user.password().stripTrailing().toUpperCase(Locale.ROOT)),
                user.roles().contains("ADMIN") ? "A" : "U"));
    }

    private void validateWidth(String field, String value) {
        if (value.stripTrailing().toUpperCase(Locale.ROOT).length() > 8) {
            throw new IllegalArgumentException("Configured security " + field
                    + " must be at most 8 characters after trimming trailing whitespace (USRSEC PIC X(08))");
        }
    }
}
