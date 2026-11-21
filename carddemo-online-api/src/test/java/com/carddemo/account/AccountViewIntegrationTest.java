package com.carddemo.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.account.AccountViewController.Lookup;
import com.carddemo.account.AccountViewService.Account;
import com.carddemo.account.AccountViewService.Customer;
import com.carddemo.account.AccountViewService.Screen;
import com.carddemo.trantype.web.ErrorResponse;
import java.math.BigDecimal;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "carddemo.db.lock-timeout=300ms")
@Testcontainers
@ActiveProfiles("dev")
class AccountViewIntegrationTest {
    private static final String URL = "/api/accounts/view";
    private static final String FOUND = "00000000042";
    private static final Account ACCOUNT = new Account("Y", new BigDecimal("-1234.56"), new BigDecimal("9000.00"),
            new BigDecimal("300.00"), "2020-01-01", "2030-01-01", "2025-03-01",
            new BigDecimal("25.01"), new BigDecimal("6.50"), "GROUP");
    private static final Customer CUSTOMER = new Customer("000000007", "Example", "Q", "Fixture",
            "123 Sample St", "Unit 2", "Sample City", "TS", "USA", "01234-5678", "(000)000-0000",
            "(000)000-0001", "000001234", "TEST-ID", "1980-02-03", "0000000812", "Y", "075");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired DataSource dataSource;
    private static final HttpHeaders headers = new HttpHeaders();

    @BeforeAll
    static void givenSyntheticMastersAndUserSession(@Autowired JdbcTemplate jdbc, @Autowired TestRestTemplate rest) {
        jdbc.update("""
                INSERT INTO account VALUES
                  (42, 'Y', -1234.56, 9000.00, 300.00, '2020-01-01', '2030-01-01', '2025-03-01',
                   25.01, 6.50, '77777', 'GROUP', '')
                """);
        jdbc.update("""
                INSERT INTO account
                SELECT id, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date,
                       reissue_date, current_cycle_credit, current_cycle_debit, zip_code, account_group_id, filler
                FROM account CROSS JOIN (VALUES (43), (47)) AS ids(id) WHERE account_id = 42
                """);
        jdbc.update("""
                INSERT INTO customer VALUES
                  (7, 'Example', 'Q', 'Fixture', '123 Sample St', 'Unit 2', 'Sample City', 'TS', 'USA',
                   '01234-5678', '(000)000-0000', '(000)000-0001', 1234, 'TEST-ID', '1980-02-03',
                   '0000000812', 'Y', 75, '')
                """);
        jdbc.update("""
                INSERT INTO card_xref VALUES
                  ('0000000000000042', 7, 42, ''),
                  ('0000000000000043', 8, 43, ''),
                  ('0000000000000044', 7, 44, ''),
                  ('0000000000000045', 8, 45, ''),
                  ('0000000000000046', 7, 46, ''),
                  ('0000000000000047', 7, 46, '')
                """);
        var login = rest.postForEntity("/api/auth/login",
                Map.of("userId", "user0001", "password", "password"), Login.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        headers.setBearerAuth(login.getBody().token());
    }

    record Login(String token) { }

    private ResponseEntity<Screen> view(String accountId) {
        return rest.postForEntity(URL, new HttpEntity<>(new Lookup(accountId), headers), Screen.class);
    }

    private Screen screen(String accountId) {
        var response = view(accountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        return response.getBody();
    }

    @Test
    void mainRequiresAuthentication() {
        assertThat(rest.postForEntity(URL, new Lookup(FOUND), ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"           ", "*", "*   "})
    void editsTreatBlankAndStarAsNoInput(String value) {
        assertThat(screen(value)).isEqualTo(Screen.invalidAccount("NO_INPUT", "No input received"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "42", "000000000042", " 0000000042", "0000000004X", "０００００００００４２"})
    void editsRequireElevenAsciiDigitsAndNonzero(String value) {
        assertThat(screen(value)).isEqualTo(Screen.invalidAccount("INVALID_ACCOUNT_ID",
                "Account Filter must  be a non-zero 11 digit number"));
    }

    @Test
    void readsPopulateBothSetsOfProtectedFieldsWithLeadingZerosAndExactMoney() {
        assertThat(screen(FOUND)).isEqualTo(new Screen(ACCOUNT, CUSTOMER, null));
    }

    @Test
    void missingXrefStopsEvenWhenTheAccountExists() {
        assertThat(screen("00000000047")).isEqualTo(Screen.invalidAccount("XREF_NOT_FOUND",
                "Account:00000000047 not found in Cross ref file."));
    }

    @Test
    void duplicateXrefUsesTheOtherBranchEvenWhenBothCardsPointToTheSameCustomer() {
        assertThat(screen("00000000046")).isEqualTo(Screen.invalidAccount("XREF_DUPLICATE",
                "File Error: READ on CXACAIX returned RESP 15 REAS 140"));
    }

    @Test
    void missingAccountStillShowsCustomerAndFirstErrorWinsWhenBothAreMissing() {
        var response = screen("00000000044");
        assertThat(response.account()).isNull();
        assertThat(response.customer()).isEqualTo(CUSTOMER);
        assertThat(response.error()).isEqualTo(new AccountViewService.Problem("ACCOUNT_NOT_FOUND",
                "Account:00000000044 not found in Acct Master file.", true));
        assertThat(screen("00000000045")).isEqualTo(Screen.invalidAccount("ACCOUNT_NOT_FOUND",
                "Account:00000000045 not found in Acct Master file."));
    }

    @Test
    void missingCustomerKeepsAccountAndDoesNotMarkAccountNumberInvalid() {
        assertThat(screen("00000000043")).isEqualTo(new Screen(ACCOUNT, null,
                new AccountViewService.Problem("CUSTOMER_NOT_FOUND",
                        "CustId:000000008 not found in customer master.", false)));
    }

    @Test
    void readFailureReturnsSafeUnavailableMessage() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("LOCK TABLE account IN ACCESS EXCLUSIVE MODE");
                var response = rest.postForEntity(URL, new HttpEntity<>(new Lookup(FOUND), headers), ErrorResponse.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getHeaders().getCacheControl()).contains("no-store");
                assertThat(response.getBody()).isEqualTo(new ErrorResponse("ACCOUNT_VIEW_UNAVAILABLE",
                        "Unable to look up account"));
            } finally {
                connection.rollback();
            }
        }
    }
}
