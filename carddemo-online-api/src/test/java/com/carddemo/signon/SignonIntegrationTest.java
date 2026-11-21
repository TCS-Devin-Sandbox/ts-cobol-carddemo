package com.carddemo.signon;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("dev")
class SignonIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserSecurity users;

    private Map<String, Object> login(String userId, String password) {
        var response = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", userId, "password", password)),
                new ParameterizedTypeReference<Map<String, Object>>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    void COSGN00C_MAIN_PARA_READ_USER_SEC_FILE_JWT_COMMAREA() throws Exception {
        var response = login("admin001", "password");
        assertThat(response).containsEntry("userId", "ADMIN001").containsEntry("userType", "A")
                .containsEntry("nextScreen", "CA00");
        var claims = SignedJWT.parse((String) response.get("token")).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("ADMIN001");
        assertThat(claims.getStringClaim("user_type")).isEqualTo("A");
        assertThat(claims.getStringClaim("from_tranid")).isEqualTo("CC00");
        assertThat(claims.getStringClaim("from_program")).isEqualTo("COSGN00C");
        assertThat(claims.getStringListClaim("roles")).containsExactly("ADMIN");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM user_security WHERE user_id='ADMIN001'", String.class))
                .startsWith("$2").isNotEqualTo("PASSWORD");
        users.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_security", Integer.class)).isEqualTo(4);
        assertThat(login("user0001", "PaSsWoRd")).containsEntry("userType", "U").containsEntry("nextScreen", "CM00");
    }
}
