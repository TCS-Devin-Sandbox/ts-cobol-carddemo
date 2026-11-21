package com.carddemo.signon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.carddemo.trantype.security.SecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserSecurityTest {
    private static final String HAS_USERS = "SELECT EXISTS (SELECT 1 FROM user_security)";

    @Test
    void MAIN_PARA_rejectsOverlengthConfiguredCredentialsBeforeSeeding() {
        for (var user : List.of(
                new SecurityProperties.User("NINECHARS   ", "PASSWORD", List.of("USER")),
                new SecurityProperties.User("TEST", "NINECHARS   ", List.of("USER")))) {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            var encoder = mock(BCryptPasswordEncoder.class);
            var valid = new SecurityProperties.User("VALID", "PASSWORD", List.of("USER"));
            var security = new UserSecurity(jdbc, encoder,
                    new SecurityProperties("unused", 60, List.of(valid, user)));
            String field = user.username().startsWith("NINE") ? "user ID" : "password";
            assertThatThrownBy(() -> security.run(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Configured security " + field
                            + " must be at most 8 characters after trimming trailing whitespace (USRSEC PIC X(08))");
            verifyNoInteractions(jdbc, encoder);
        }
    }

    @Test
    void MAIN_PARA_acceptsEightCharacterCredentialsWithTrailingPaddingAndEmptyConfiguration() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(HAS_USERS, Boolean.class)).thenReturn(false);
        var encoder = new BCryptPasswordEncoder();
        var security = new UserSecurity(jdbc, encoder, new SecurityProperties("unused", 60,
                List.of(new SecurityProperties.User("user0001   ", "password   ", List.of("USER")))));
        security.run(null);
        var hash = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), eq("USER0001"), hash.capture(), eq("U"));
        assertThat(encoder.matches("PASSWORD", hash.getValue())).isTrue();

        JdbcTemplate emptyJdbc = mock(JdbcTemplate.class);
        new UserSecurity(emptyJdbc, encoder, new SecurityProperties("unused", 60, List.of())).run(null);
        verifyNoInteractions(emptyJdbc);
    }

    @Test
    void MAIN_PARA_doesNotSeedConfiguredUsersWhenTableHasAnyUsers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(HAS_USERS, Boolean.class)).thenReturn(true);
        var encoder = mock(BCryptPasswordEncoder.class);
        var security = new UserSecurity(jdbc, encoder, new SecurityProperties("unused", 60,
                List.of(new SecurityProperties.User("MISSING", "PASSWORD", List.of("ADMIN")))));

        security.run(null);

        verify(jdbc).queryForObject(HAS_USERS, Boolean.class);
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(encoder);
    }

    @Test
    void READ_USER_SEC_FILE_matchesFixedWidthPasswordPadding() {
        for (String seedPassword : List.of("ABC", "ABC   ")) {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForObject(HAS_USERS, Boolean.class)).thenReturn(false);
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
