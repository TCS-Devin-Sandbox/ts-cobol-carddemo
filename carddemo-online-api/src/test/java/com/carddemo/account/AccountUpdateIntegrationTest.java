package com.carddemo.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.account.AccountUpdateService.Change;
import com.carddemo.account.AccountUpdateService.Screen;
import com.carddemo.trantype.web.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "carddemo.db.lock-timeout=300ms")
@Testcontainers
@ActiveProfiles("dev")
class AccountUpdateIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    private HttpHeaders headers;
    private static final String ID = "00000000042";
    private static final String READ = "/api/accounts/update/read";
    private static final String UPDATE = "/api/accounts/update";

    record Login(String token) { }

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM card_xref"); jdbc.update("DELETE FROM account"); jdbc.update("DELETE FROM customer");
        jdbc.update("""
                INSERT INTO account VALUES
                  (42, 'Y', -1234.56, 9000.00, 300.00, '2020-01-01', '2030-01-01', '2025-03-01',
                   25.01, 6.50, '77777', 'GROUP', 'KEEP')
                """);
        jdbc.update("""
                INSERT INTO customer VALUES
                  (7, 'Example', 'Q', 'Fixture', '123 Sample St', '', 'Sample City', 'CA', 'USA',
                   '90210', '(201)234-5678', '', 123456789, 'TEST-ID', '1980-02-03',
                   '0000000812', 'Y', 750, 'KEEP')
                """);
        jdbc.update("INSERT INTO card_xref VALUES ('0000000000000042', 7, 42, '')");
        var login = rest.postForEntity("/api/auth/login", Map.of("userId", "user0001", "password", "password"), Login.class);
        headers = new HttpHeaders(); headers.setBearerAuth(login.getBody().token());
    }

    private Screen read(String id) {
        var response = rest.postForEntity(READ, new HttpEntity<>(Map.of("accountId", id), headers), Screen.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        return response.getBody();
    }

    private Change change(Screen screen, String name, boolean confirmed) {
        var fields = new LinkedHashMap<>(screen.fields()); fields.put("firstName", name); fields.put("creditLimit", "100.99");
        return new Change(screen.accountId(), screen.version(), fields, confirmed);
    }

    private Screen update(Change change) {
        var response = rest.postForEntity(UPDATE, new HttpEntity<>(change, headers), Screen.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        return response.getBody();
    }

    @Test
    void p0000RequiresSessionAndP3100StartsWithSearchValidation() {
        assertThat(rest.postForEntity(READ, Map.of("accountId", ID), ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.postForEntity(UPDATE, new Change(ID, "x", Map.of(), true), ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(read("").state()).isEqualTo("SEARCH");
        assertThat(read("42").invalidField()).isEqualTo("accountId");
    }

    @Test
    void p9000p9200p9300p9400p9500ReadBothMastersAndHandleMissingAndDuplicateXref() {
        Screen screen = read(ID);
        assertThat(screen.customerId()).isEqualTo("000000007");
        assertThat(screen.fields()).containsAllEntriesOf(AccountUpdateEditsTest.valid());
        assertThat(screen.version()).isNotBlank();
        assertThat(read("00000000099").message()).isEqualTo("Account:00000000099 not found in Cross ref file.");
        jdbc.update("INSERT INTO card_xref VALUES ('0000000000000043', 7, 42, '')");
        assertThat(read(ID).message()).contains("RESP 15");
        jdbc.update("DELETE FROM card_xref WHERE card_number = '0000000000000043'");
        jdbc.update("DELETE FROM account WHERE account_id = 42");
        assertThat(read(ID).message()).contains("Acct Master");
        jdbc.update("DELETE FROM customer WHERE customer_id = 7");
        assertThat(read(ID).message()).contains("Acct Master");
    }

    @Test
    void p9400MissingCustomer() {
        jdbc.update("DELETE FROM customer WHERE customer_id = 7");
        assertThat(read(ID).message()).isEqualTo("CustId:000000007 not found in customer master.");
    }

    @Test
    void p1205p2000NoChangeBeforeValidationAndConfirmationDoesNotWriteAndF12RereadsOriginal() {
        jdbc.update("UPDATE customer SET fico_credit_score = 75 WHERE customer_id = 7");
        Screen old = read(ID);
        assertThat(update(new Change(ID, old.version(), old.fields(), false)).message()).isEqualTo(AccountUpdateService.NO_CHANGE);
        jdbc.update("UPDATE customer SET fico_credit_score = 750 WHERE customer_id = 7");
        old = read(ID);
        Screen review = update(change(old, "Updated", false));
        assertThat(review.state()).isEqualTo("CONFIRM");
        assertThat(review.message()).isEqualTo("Changes validated.Press F5 to save");
        assertThat(read(ID).fields()).isEqualTo(old.fields());
        Screen saved = update(change(old, "Updated", true));
        assertThat(saved.state()).isEqualTo("SAVED");
        assertThat(saved.message()).isEqualTo("Changes committed to database");
        assertThat(saved.version()).isNotEqualTo(old.version());
        assertThat(saved.fields()).containsEntry("firstName", "Updated").containsEntry("creditLimit", "100.99");
        assertThat(saved.fields()).containsEntry("accountGroupId", "");
        assertThat(jdbc.queryForObject("SELECT rtrim(zip_code) FROM account WHERE account_id = 42", String.class)).isEqualTo("GROUP");
        assertThat(jdbc.queryForObject("SELECT rtrim(filler) FROM account WHERE account_id = 42", String.class)).isEqualTo("KEEP");
    }

    @Test
    void p1200RejectsInvalidChangesEvenWithConfirmation() {
        Screen old = read(ID);
        Screen invalid = update(change(old, "Invalid1", true));
        assertThat(invalid.state()).isEqualTo("EDIT");
        assertThat(invalid.invalidField()).isEqualTo("firstName");
        assertThat(read(ID).fields()).isEqualTo(old.fields());
    }

    @Test
    void p1205PunctuationOnlyPhoneChangeDoesNotWriteEitherMaster() {
        Screen old = read(ID);
        var fields = new LinkedHashMap<>(old.fields());
        fields.put("phoneNumber1", "x201x234x5678");
        fields.put("phoneNumber2", "(   )   -");
        var unchanged = update(new Change(ID, old.version(), fields, true));
        assertThat(unchanged.message()).isEqualTo(AccountUpdateService.NO_CHANGE);
        assertThat(read(ID).version()).isEqualTo(old.version());
        assertThat(read(ID).fields()).isEqualTo(old.fields());
    }

    @Test
    void p1200RejectsOversizedCompactDateWithoutPersistingTruncatedValue() {
        Screen old = read(ID);
        var fields = new LinkedHashMap<>(old.fields()); fields.put("openDate", "202001011");
        var invalid = update(new Change(ID, old.version(), fields, true));
        assertThat(invalid.invalidField()).isEqualTo("openDate");
        assertThat(invalid.message()).isEqualTo("Open Date:day must be a number between 1 and 31.");
        assertThat(read(ID).version()).isEqualTo(old.version());
    }

    @Test
    void p9600ReconstructsPhonePunctuationAndDateSeparatorsAndKeepsCountryProtected() {
        Screen old = read(ID);
        var fields = new LinkedHashMap<>(old.fields());
        fields.put("phoneNumber1", "x201x234x5678");
        fields.put("openDate", "20210203");
        fields.put("countryCode", "XYZ");
        Screen saved = update(new Change(ID, old.version(), fields, true));
        assertThat(saved.fields()).containsEntry("phoneNumber1", "(201)234-5678")
                .containsEntry("phoneNumber2", "(   )   -")
                .containsEntry("openDate", "2021-02-03").containsEntry("countryCode", "USA");
    }

    @Test
    void p9700DetectsChangesByAnyWriterToEitherMasterEvenUneditedFields() {
        for (String table : List.of("account", "customer")) {
            Screen old = read(ID);
            jdbc.update("UPDATE " + table + " SET filler = 'CHANGED'");
            var response = rest.postForEntity(UPDATE, new HttpEntity<>(change(old, "Updated", true), headers), ErrorResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().message()).isEqualTo(AccountUpdateService.CONFLICT);
            assertThat(response.getHeaders().getCacheControl()).contains("no-store");
            assertThat(read(ID).fields().get("firstName")).isEqualTo("Example");
        }
    }

    @Test
    void p9600p9700ConcurrentUpdatesFromSameReadExactlyOneWinsAndDatabaseHoldsWinner() throws Exception {
        Screen old = read(ID);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of("Winner Alpha", "Winner Beta").stream().map(name -> executor.submit(() -> {
                ready.countDown(); assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                var fields = new LinkedHashMap<>(old.fields()); fields.put("firstName", name);
                fields.put("creditLimit", name.endsWith("Alpha") ? "111.11" : "222.22");
                var response = rest.postForEntity(UPDATE,
                        new HttpEntity<>(new Change(ID, old.version(), fields, true), headers), Map.class);
                return Map.entry(fields, response);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var first = futures.get(0).get(10, TimeUnit.SECONDS); var second = futures.get(1).get(10, TimeUnit.SECONDS);
            assertThat(List.of(first.getValue().getStatusCode(), second.getValue().getStatusCode()))
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            var winner = first.getValue().getStatusCode().is2xxSuccessful() ? first : second;
            var loser = first.getValue().getStatusCode().is2xxSuccessful() ? second : first;
            assertThat(loser.getValue().getBody().get("message")).isEqualTo(AccountUpdateService.CONFLICT);
            assertThat(read(ID).fields()).containsEntry("firstName", winner.getKey().get("firstName"))
                    .containsEntry("creditLimit", winner.getKey().get("creditLimit"));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void p9700LocksCrossReferenceBeforeResolvingAndRewritingCustomer() throws Exception {
        Screen old = read(ID);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("SELECT * FROM card_xref WHERE account_id = 42 FOR UPDATE");
                var response = rest.postForEntity(UPDATE,
                        new HttpEntity<>(change(old, "Updated", true), headers), ErrorResponse.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(read(ID).fields()).isEqualTo(old.fields());
            } finally { connection.rollback(); }
        }
        assertThat(update(change(old, "Updated", true)).customerId()).isEqualTo("000000007");
        old = read(ID);
        jdbc.update("UPDATE card_xref SET customer_id = 8 WHERE account_id = 42");
        var response = rest.postForEntity(UPDATE,
                new HttpEntity<>(change(old, "Other", true), headers), ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void p9600RollsBackAccountWhenCustomerRewriteFails() {
        Screen old = read(ID);
        jdbc.execute("""
                CREATE FUNCTION reject_customer_update() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'synthetic failure'; END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER reject_customer BEFORE UPDATE ON customer FOR EACH ROW EXECUTE FUNCTION reject_customer_update()");
        try {
            var response = rest.postForEntity(UPDATE, new HttpEntity<>(change(old, "Updated", true), headers), ErrorResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().message()).isEqualTo("Changes unsuccessful. Please try again");
            assertThat(read(ID).fields()).isEqualTo(old.fields());
        } finally {
            jdbc.execute("DROP TRIGGER reject_customer ON customer"); jdbc.execute("DROP FUNCTION reject_customer_update()");
        }
    }

    @Test
    void abendRoutineDoesNotExposeDatabaseDiagnostics() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("LOCK TABLE account IN ACCESS EXCLUSIVE MODE");
                var response = rest.postForEntity(READ, new HttpEntity<>(Map.of("accountId", ID), headers), ErrorResponse.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody().message()).isEqualTo("Changes unsuccessful. Please try again");
            } finally { connection.rollback(); }
        }
    }
}
