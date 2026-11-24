package com.carddemo.vsammq;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.trantype.web.ErrorResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CDRA over REST against PostgreSQL loaded from the posting fixtures' ACCTDATA records. The expected reply is cut
 * from the raw CVACT01Y bytes, so balances keep the fixture's own overpunched sign characters.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "carddemo.db.lock-timeout=300ms")
@Testcontainers
@ActiveProfiles("dev")
class AccountInquiryIntegrationTest {
    private static final Path FIXTURES = Path.of("..", "fixtures", "posting");
    private static final Map<String, String> RECORDS = fixtureAccounts();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired DataSource dataSource;
    private static final HttpHeaders headers = new HttpHeaders();

    @BeforeAll
    static void givenPostingFixtureAccountsAndUserSession(@Autowired JdbcTemplate jdbc,
                                                         @Autowired TestRestTemplate rest) {
        for (String r : RECORDS.values()) {
            jdbc.update("INSERT INTO account VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Long.parseLong(r.substring(0, 11)), r.substring(11, 12), zoned(r.substring(12, 24)),
                    zoned(r.substring(24, 36)), zoned(r.substring(36, 48)), r.substring(48, 58),
                    r.substring(58, 68), r.substring(68, 78), zoned(r.substring(78, 90)), zoned(r.substring(90, 102)),
                    r.substring(102, 112), r.substring(112, 122), r.substring(122, 300));
        }
        var login = rest.postForEntity("/api/auth/login",
                Map.of("userId", "user0001", "password", "password"), Login.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        headers.setBearerAuth(login.getBody().token());
    }

    record Login(String token) { }

    static List<String> fixtureAccountIds() {
        return List.copyOf(RECORDS.keySet());
    }

    @Test
    void postingFixturesSupplyDistinctAccounts() {
        assertThat(RECORDS).hasSizeGreaterThanOrEqualTo(6);
    }

    @ParameterizedTest
    @MethodSource("fixtureAccountIds")
    void inquiryRepliesWithTheLabelledFixtureFieldsPaddedTo1000(String accountId) {
        String r = RECORDS.get(accountId);
        String expected = "ACCOUNT ID : " + r.substring(0, 11) + "ACCOUNT STATUS : " + r.substring(11, 12)
                + "BALANCE : " + r.substring(12, 24) + "CREDIT LIMIT : " + r.substring(24, 36)
                + "CASH LIMIT : " + r.substring(36, 48) + "OPEN DATE : " + r.substring(48, 58)
                + "EXPR DATE : " + r.substring(58, 68) + "REIS DATE : " + r.substring(68, 78)
                + "CREDIT BAL : " + r.substring(78, 90) + "DEBIT BAL : " + r.substring(90, 102)
                + "GROUP ID : " + r.substring(112, 122);
        expected += " ".repeat(1000 - expected.length());

        assertReply(get(accountId), "FOUND", expected);
        assertReply(post("INQA" + accountId), "FOUND", expected);
        assertReply(post("INQA" + accountId + "trailing request filler is ignored"), "FOUND", expected);
    }

    @Test
    void accountTextOutsideAsciiStaysOneBytePerCharacter(@Autowired JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO account VALUES (77, 'Y', 0, 0, 0, '2020-01-01', '2030-01-01', '2030-01-01', 0, 0,"
                + " ' ', 'GRÜPPE', ' ')");
        var response = rest.exchange("/api/accounts/00000000077/inquiry", HttpMethod.GET, new HttpEntity<>(headers),
                byte[].class);
        assertThat(response.getHeaders().getContentType().getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(response.getBody()).hasSize(1000);
        assertThat(new String(response.getBody(), StandardCharsets.ISO_8859_1).substring(0, 252))
                .endsWith("GROUP ID : GRÜPPE    ");
    }

    @Test
    void missingAccountRepliesNotFoundWithTheKey() {
        String expected = pad("INVALID REQUEST PARAMETERS ACCT ID : 00000009999");
        assertReply(get("00000009999"), "NOT_FOUND", expected);
        assertReply(post("INQA00000009999"), "NOT_FOUND", expected);
    }

    @Test
    void badFunctionOrZeroKeyRepliesInvalidWithoutReading() {
        assertReply(post("DATE00000001001"), "INVALID_REQUEST",
                pad("INVALID REQUEST PARAMETERS ACCT ID : 00000001001FUNCTION : DATE"));
        assertReply(get("00000000000"), "INVALID_REQUEST",
                pad("INVALID REQUEST PARAMETERS ACCT ID : 00000000000FUNCTION : INQA"));
        assertReply(post("INQA"), "INVALID_REQUEST",
                pad("INVALID REQUEST PARAMETERS ACCT ID :            FUNCTION : INQA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1001", "000000010010", "0000000100X"})
    void getRequiresElevenDigits(String accountId) {
        var response = rest.exchange("/api/accounts/" + accountId + "/inquiry", HttpMethod.GET,
                new HttpEntity<>(headers), ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("INVALID_ACCOUNT_ID",
                "Account ID must be 11 digits"));
    }

    @Test
    void postRejectsMessagesLongerThanTheMqBufferOrNotAscii() {
        for (String body : List.of("INQA00000001001" + " ".repeat(986), "INQA0000000100\u00e9")) {
            var response = rest.exchange("/api/accounts/inquiry", HttpMethod.POST, text(body), ErrorResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("INVALID_MESSAGE");
        }
    }

    @Test
    void inquiryRequiresAuthentication() {
        assertThat(rest.getForEntity("/api/accounts/00000001001/inquiry", ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void readFailureReturnsSafeUnavailableMessage() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("LOCK TABLE account IN ACCESS EXCLUSIVE MODE");
                var response = rest.exchange("/api/accounts/00000001001/inquiry", HttpMethod.GET,
                        new HttpEntity<>(headers), ErrorResponse.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isEqualTo(new ErrorResponse("ACCOUNT_INQUIRY_UNAVAILABLE",
                        "Unable to read account"));
            } finally {
                connection.rollback();
            }
        }
    }

    private ResponseEntity<String> get(String accountId) {
        return rest.exchange("/api/accounts/" + accountId + "/inquiry", HttpMethod.GET, new HttpEntity<>(headers),
                String.class);
    }

    private ResponseEntity<String> post(String message) {
        return rest.exchange("/api/accounts/inquiry", HttpMethod.POST, text(message), String.class);
    }

    private static HttpEntity<String> text(String body) {
        var h = new HttpHeaders();
        h.putAll(headers);
        h.setContentType(MediaType.TEXT_PLAIN);
        return new HttpEntity<>(body, h);
    }

    private static void assertReply(ResponseEntity<String> response, String outcome, String expected) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(AccountInquiryController.TEXT_LATIN1))
                .isTrue();
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst(AccountInquiryController.OUTCOME_HEADER)).isEqualTo(outcome);
        assertThat(response.getBody()).hasSize(1000).isEqualTo(expected);
    }

    private static String pad(String text) {
        return text + " ".repeat(1000 - text.length());
    }

    /** ASCII rendering of an EBCDIC zoned S9(10)V99: last byte {A-I positive, }J-R negative. */
    private static BigDecimal zoned(String field) {
        char last = field.charAt(11);
        String positive = "{ABCDEFGHI";
        String negative = "}JKLMNOPQR";
        int digit = positive.indexOf(last) >= 0 ? positive.indexOf(last) : negative.indexOf(last);
        if (digit < 0) {
            throw new IllegalArgumentException("Not an overpunched digit: " + field);
        }
        var value = new BigDecimal(field.substring(0, 11) + digit).movePointLeft(2);
        return negative.indexOf(last) >= 0 ? value.negate() : value;
    }

    /** Every ACCTDATA record across the posting cases, keyed by account id (identical duplicates collapse). */
    private static Map<String, String> fixtureAccounts() {
        var records = new TreeMap<String, String>();
        try (Stream<Path> cases = Files.list(FIXTURES)) {
            for (Path c : cases.sorted().toList()) {
                Path file = c.resolve("input").resolve("AWS.M2.CARDDEMO.ACCTDATA.PS.txt");
                if (!Files.exists(file)) {
                    continue;
                }
                for (String line : Files.readAllLines(file)) {
                    String r = line + " ".repeat(300 - line.length());
                    String previous = records.putIfAbsent(r.substring(0, 11), r);
                    if (previous != null && !previous.equals(r)) {
                        throw new IllegalStateException("Fixture accounts disagree for " + r.substring(0, 11));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return records;
    }
}
