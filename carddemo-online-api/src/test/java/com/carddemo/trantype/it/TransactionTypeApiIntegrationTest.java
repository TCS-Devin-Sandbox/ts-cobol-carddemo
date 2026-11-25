package com.carddemo.trantype.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.signon.UserSecurity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-end against PostgreSQL 15 with the Flyway migrations and seed data from db/migration. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("dev")
@TestPropertySource(properties = {"spring.flyway.clean-disabled=false", "carddemo.db.lock-timeout=1s"})
class TransactionTypeApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    @Autowired
    TestRestTemplate rest;

    @Autowired
    Flyway flyway;

    @Autowired
    FlywayMigrationStrategy migrations;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserSecurity users;

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        migrations.migrate(flyway);
        users.run(null);
    }

    private String token(String user, String password) {
        ResponseEntity<Map<String, Object>> res = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", user, "password", password)), MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) res.getBody().get("token");
    }

    private ResponseEntity<Map<String, Object>> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token("admin", "admin123"));
        return rest.exchange(url, method, new HttpEntity<>(body, headers), MAP);
    }

    @SuppressWarnings("unchecked")
    private static List<String> keys(ResponseEntity<Map<String, Object>> res) {
        return ((List<Map<String, Object>>) res.getBody().get("rows")).stream()
                .map(r -> (String) r.get("trType")).toList();
    }

    @Test
    void sharedVsamMigrationsUpgradeExistingDatabaseWithSchemaScopedRole() {
        var password = UUID.randomUUID().toString();
        jdbc.execute("CREATE ROLE online_migrator LOGIN PASSWORD '" + password + "'");
        jdbc.execute("CREATE SCHEMA online_upgrade AUTHORIZATION online_migrator");
        assertThat(jdbc.queryForObject("""
                SELECT has_database_privilege('online_migrator', current_database(), 'CREATE')
                """, Boolean.class)).isFalse();

        var upgradeDataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), "online_migrator", password);
        var existingMigrations = Flyway.configure().dataSource(upgradeDataSource).schemas("online_upgrade")
                .createSchemas(false).load();
        existingMigrations.migrate();
        jdbc.update("UPDATE online_upgrade.transaction_type SET tr_description = 'EXISTING' WHERE tr_type = '01'");

        migrations.migrate(existingMigrations);

        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'online_upgrade' AND table_name NOT IN
                    ('flyway_schema_history', 'flyway_vsam_schema_history', 'transaction_type',
                     'transaction_type_category', 'user_security')
                """, String.class)).containsExactlyInAnyOrder("account", "card", "customer", "card_xref",
                "transaction_category_balance", "disclosure_group", "transaction", "tran_type", "tran_category",
                "pending_auth_summary", "pending_auth_detail", "auth_fraud", "pending_auth_unload_frame");
        assertThat(jdbc.queryForObject("""
                SELECT tr_description FROM online_upgrade.transaction_type WHERE tr_type = '01'
                """, String.class)).isEqualTo("EXISTING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM online_upgrade.tran_type", Integer.class)).isZero();
    }

    @Test
    void firstPageShowsSevenSeededRows() {
        var res = call(HttpMethod.GET, "/api/transaction-types", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(keys(res)).containsExactly("01", "02", "03", "04", "05", "06", "07");
        assertThat(res.getBody()).containsEntry("hasNext", false).containsEntry("hasPrev", false)
                .containsEntry("pageNumber", 1).containsEntry("firstKey", "01").containsEntry("lastKey", "07");
    }

    @Test
    void healthIsPublic() {
        var response = rest.exchange("/actuator/health", HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void updateBlockedByRowLock() throws Exception {
        try (var locker = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            locker.setAutoCommit(false);
            try {
                locker.createStatement().execute("SELECT * FROM transaction_type WHERE tr_type = '05' FOR UPDATE");
                var inline = call(HttpMethod.PATCH, "/api/transaction-types/05", Map.of("trDescription", "LOCKED"));
                assertThat(inline.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(inline.getBody()).containsEntry("code", "LOCK_ERROR")
                        .containsEntry("message", "Deadlock. Someone else updating ?");
                var edit = call(HttpMethod.PUT, "/api/transaction-types/05", Map.of("trDescription", "LOCKED"));
                assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(edit.getBody()).containsEntry("code", "LOCK_ERROR")
                        .containsEntry("message", "Could not lock record for update");
            } finally {
                locker.rollback();
            }
        }
        assertThat(jdbc.queryForObject("SELECT tr_description FROM transaction_type WHERE tr_type='05'",
                String.class)).isEqualTo("REFUND");
    }

    @Test
    void forwardAndBackwardKeysetPaging() {
        var p1 = call(HttpMethod.GET, "/api/transaction-types?size=3", null);
        assertThat(keys(p1)).containsExactly("01", "02", "03");
        assertThat(p1.getBody()).containsEntry("hasNext", true).containsEntry("nextCursor", "04");

        var p2 = call(HttpMethod.GET, "/api/transaction-types?size=3&cursor=04&direction=FORWARD&page=2", null);
        assertThat(keys(p2)).containsExactly("04", "05", "06");
        assertThat(p2.getBody()).containsEntry("hasPrev", true).containsEntry("pageNumber", 2);

        var p3 = call(HttpMethod.GET, "/api/transaction-types?size=3&cursor=07&direction=FORWARD&page=3", null);
        assertThat(keys(p3)).containsExactly("07");
        assertThat(p3.getBody()).containsEntry("hasNext", false).containsEntry("pageNumber", 3);

        var back = call(HttpMethod.GET, "/api/transaction-types?size=3&cursor=04&direction=BACKWARD&page=1", null);
        assertThat(keys(back)).containsExactly("01", "02", "03");
        assertThat(back.getBody()).containsEntry("hasPrev", false).containsEntry("hasNext", true)
                .containsEntry("nextCursor", "04").containsEntry("pageNumber", 1);
    }

    @Test
    void filters() {
        assertThat(keys(call(HttpMethod.GET, "/api/transaction-types?typeFilter=3", null))).containsExactly("03");
        assertThat(keys(call(HttpMethod.GET, "/api/transaction-types?descFilter=RE", null)))
                .containsExactly("03", "05", "06");
        var none = call(HttpMethod.GET, "/api/transaction-types?descFilter=ZZZ", null);
        assertThat(none.getBody()).containsEntry("message", "No Records found for these filter conditions");
        var bad = call(HttpMethod.GET, "/api/transaction-types?typeFilter=X", null);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody()).containsEntry("message", "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER");
    }

    @Test
    void emptyTable() {
        jdbc.update("DELETE FROM transaction_type_category");
        jdbc.update("DELETE FROM transaction_type");
        var res = call(HttpMethod.GET, "/api/transaction-types", null);
        assertThat(keys(res)).isEmpty();
        assertThat(res.getBody()).containsEntry("message", "No records found for this search condition.");
    }

    @Test
    void inlineUpdate() {
        var ok = call(HttpMethod.PATCH, "/api/transaction-types/06", Map.of("trDescription", "REVERSAL"));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT tr_description FROM transaction_type WHERE tr_type='06'",
                String.class)).isEqualTo("REVERSAL");

        var blank = call(HttpMethod.PATCH, "/api/transaction-types/06", Map.of("trDescription", " "));
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody()).containsEntry("message", "Transaction Desc must be supplied.");

        var invalid = call(HttpMethod.PATCH, "/api/transaction-types/06", Map.of("trDescription", "R#V"));
        assertThat(invalid.getBody()).containsEntry("message", "Transaction Desc can have numbers or alphabets only.");

        var same = call(HttpMethod.PATCH, "/api/transaction-types/06", Map.of("trDescription", "reversal"));
        assertThat(same.getBody()).containsEntry("message", "No change detected with respect to database values.");

        var gone = call(HttpMethod.PATCH, "/api/transaction-types/42", Map.of("trDescription", "X"));
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gone.getBody()).containsEntry("message", "Record not found. Deleted by others ?");
    }

    @Test
    void deleteWithAndWithoutChildren() {
        var restricted = call(HttpMethod.DELETE, "/api/transaction-types/01", null);
        assertThat(restricted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) restricted.getBody().get("message"))
                .startsWith("Please delete associated child records first:");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_type WHERE tr_type='01'", Integer.class))
                .isEqualTo(1);

        jdbc.update("DELETE FROM transaction_type_category WHERE trc_type_code='05'");
        var deleted = call(HttpMethod.DELETE, "/api/transaction-types/05", null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_type WHERE tr_type='05'", Integer.class))
                .isZero();
    }

    @Test
    void addEditAndDuplicate() {
        var created = call(HttpMethod.POST, "/api/transaction-types", Map.of("trType", "8", "trDescription", "FEE"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("trType", "08");

        var dup = call(HttpMethod.POST, "/api/transaction-types", Map.of("trType", "01", "trDescription", "X"));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody()).containsEntry("code", "DUPLICATE_KEY")
                .containsEntry("message", "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:-803");

        var fetched = call(HttpMethod.GET, "/api/transaction-types/01", null);
        assertThat(fetched.getBody()).containsEntry("trDescription", "PURCHASE");

        var edited = call(HttpMethod.PUT, "/api/transaction-types/01", Map.of("trDescription", "PURCHASES"));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);

        var upsert = call(HttpMethod.PUT, "/api/transaction-types/09", Map.of("trDescription", "NEW TYPE"));
        assertThat(upsert.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var missing = call(HttpMethod.GET, "/api/transaction-types/42", null);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("message", "No record found for this key in database");
    }

    @Test
    void nonAdminRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token("user", "user123"));
        var res = rest.exchange("/api/transaction-types", HttpMethod.GET, new HttpEntity<>(headers), MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var anon = rest.exchange("/api/transaction-types", HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var badLogin = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", "admin", "password", "wrong")), MAP);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
