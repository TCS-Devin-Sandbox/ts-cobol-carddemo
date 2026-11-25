package com.carddemo.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.authorization.AuthorizationSummaryController.Request;
import com.carddemo.authorization.AuthorizationSummaryFlow.Screen;
import com.carddemo.authorization.AuthorizationSummaryFlow.State;
import com.carddemo.authorization.PendingAuthorizations.Account;
import com.carddemo.authorization.PendingAuthorizations.Row;
import com.carddemo.authorization.PendingAuthorizations.Summary;
import com.carddemo.trantype.web.ErrorResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** CPVS end to end over the G2 schema and the CAVW account/xref/customer reads. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "carddemo.db.lock-timeout=300ms")
@Testcontainers
@ActiveProfiles("dev")
class AuthorizationSummaryIntegrationTest {
    private static final String URL = "/api/authorizations/summary";
    private static final Account SECTION = new Account("000000007", "Example Q Fixture", "123 Sample St,Unit 2",
            "Sample City,TS,01234", "(000)000-0000", new BigDecimal("9000.00"), new BigDecimal("300.00"));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired DataSource dataSource;
    private static final HttpHeaders headers = new HttpHeaders();

    @BeforeAll
    static void givenSyntheticAccountsAndPendingAuthorizations(@Autowired JdbcTemplate jdbc,
                                                               @Autowired TestRestTemplate rest) {
        jdbc.update("""
                INSERT INTO account VALUES
                  (42, 'Y', -1234.56, 9000.00, 300.00, '2020-01-01', '2030-01-01', '2025-03-01',
                   25.01, 6.50, '77777', 'GROUP', '')
                """);
        jdbc.update("""
                INSERT INTO customer VALUES
                  (7, 'Example Two', 'Quinn', 'Fixture Name', '123 Sample St  Rear', 'Unit 2', 'Sample City', 'TS',
                   'USA', '01234-5678', '(000)000-0000', '(000)000-0001', 1234, 'TEST-ID', '1980-02-03',
                   '0000000812', 'Y', 75, '')
                """);
        jdbc.update("INSERT INTO card_xref VALUES ('0000000000000042', 7, 42, '')");
        for (long account : new long[] {42, 47}) {
            jdbc.update("""
                    INSERT INTO pending_auth_summary VALUES
                      (?, 7, 'A', 'A1', 'A2', 'A3', 'A4', 'A5', 9000.00, 300.00, 123.45, 0.00, 6, 1,
                       120.00, 3.45, '')
                    """, account);
        }
        for (int i = 0; i < 7; i++) {
            jdbc.update("""
                    INSERT INTO pending_auth_detail VALUES
                      (42, 73999, ?, '260102', ?, '0000000000000042', 'PURC', '1230', '0100  ', 'POS   ', 'ID0001',
                       ?, '0000', 0, 10.00, ?, '5411', 'USA', 5, 'M00000000000001', 'Sample Merchant',
                       'Sample City', 'TS', '012345678', ?, 'P', 'N', '        ', '')
                    """, 999_890_000L + i, String.format("1000%02d", 59 - i), i == 1 ? "05" : "00",
                    new BigDecimal(i == 1 ? "0.00" : "10.0" + i), "TXN00000000000" + i);
        }
        var login = rest.postForEntity("/api/auth/login",
                Map.of("userId", "user0001", "password", "password"), Login.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        headers.setBearerAuth(login.getBody().token());
    }

    record Login(String token) { }

    private Screen post(Request request) {
        var response = rest.postForEntity(URL, new HttpEntity<>(request, headers), Screen.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        return response.getBody();
    }

    @Test
    void requiresAuthentication() {
        assertThat(rest.postForEntity(URL, new Request("ENTER", "00000000042", List.of(), null), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void GATHER_DETAILS_composesAccountSectionSummaryAndFirstPageInKeyOrder() {
        var screen = post(new Request("ENTER", "00000000042", List.of(), null));
        assertThat(screen.account()).isEqualTo(SECTION);
        assertThat(screen.summary()).isEqualTo(new Summary(true, 6, 1, new BigDecimal("123.45"),
                new BigDecimal("0.00"), new BigDecimal("120.00"), new BigDecimal("3.45")));
        assertThat(screen.rows()).hasSize(5);
        assertThat(screen.rows().get(0)).isEqualTo(new Row("73999999890000", "TXN000000000000", "01/02/26",
                "10:00:59", "PURC", "A", "P", new BigDecimal("10.00")));
        assertThat(screen.rows().get(1).approval()).isEqualTo("D");
        assertThat(screen.state().nextPage()).isTrue();
    }

    @Test
    void PROCESS_PF8_KEY_and_PF7_overHttp() {
        var first = post(new Request("ENTER", "00000000042", List.of(), null));
        var second = post(new Request("PF8", null, List.of(), first.state()));
        assertThat(second.rows()).extracting(Row::transactionId)
                .containsExactly("TXN000000000005", "TXN000000000006");
        assertThat(second.state().nextPage()).isFalse();
        var back = post(new Request("PF7", null, List.of(), second.state()));
        assertThat(back.rows()).extracting(Row::transactionId).startsWith("TXN000000000000");
    }

    @Test
    void GETCARDXREF_BYACCT_notFoundStillShowsTheAuthorizationSummary() {
        var screen = post(new Request("ENTER", "00000000047", List.of(), null));
        assertThat(screen.account()).isNull();
        assertThat(screen.message())
                .isEqualTo("Account:00000000047 not found in XREF file. Resp:000000013 Reas:000000080");
        assertThat(screen.summary().found()).isTrue();
        assertThat(screen.rows()).isEmpty();
    }

    @Test
    void forgedStateAndUnknownKeysAreRejected() {
        var forged = new State("00000000042", 3, List.of(), null, false, List.of());
        for (var request : List.of(new Request("PF8", null, List.of(), forged),
                new Request("PF5", null, List.of(), null), new Request("ENTER", "00000000042", List.of("SS"), null))) {
            var response = rest.postForEntity(URL, new HttpEntity<>(request, headers), ErrorResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void readFailureReturnsSafeUnavailableMessage() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("LOCK TABLE pending_auth_summary IN ACCESS EXCLUSIVE MODE");
                var response = rest.postForEntity(URL, new HttpEntity<>(
                        new Request("ENTER", "00000000042", List.of(), null), headers), ErrorResponse.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isEqualTo(new ErrorResponse("AUTHORIZATION_SUMMARY_UNAVAILABLE",
                        "Unable to read pending authorizations"));
            } finally {
                connection.rollback();
            }
        }
    }
}
