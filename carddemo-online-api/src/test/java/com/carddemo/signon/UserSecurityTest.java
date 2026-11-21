package com.carddemo.signon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carddemo.trantype.security.SecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserSecurityTest {
    @Test
    void READ_USER_SEC_FILE_matchesFixedWidthPasswordPadding() {
        for (String seedPassword : List.of("ABC", "ABC   ")) {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            var encoder = new BCryptPasswordEncoder();
            var properties = new SecurityProperties("unused", 60,
                    List.of(new SecurityProperties.User("TEST", seedPassword, List.of("USER"))));
            var security = new UserSecurity(jdbc, encoder, properties);
            security.run(null);
            var hash = ArgumentCaptor.forClass(String.class);
            verify(jdbc).update(anyString(), eq("TEST"), hash.capture(), eq("U"));
            var user = new UserSecurity.User("TEST", hash.getValue(), "U");
            assertThat(encoder.matches("ABC", user.passwordHash())).isTrue();
            assertThat(security.matches("abc", user)).isTrue();
            assertThat(security.matches("abc   ", user)).isTrue();
            assertThat(security.matches("abd   ", user)).isFalse();
            assertThat(security.matches("", user)).isFalse();
        }
    }
}
