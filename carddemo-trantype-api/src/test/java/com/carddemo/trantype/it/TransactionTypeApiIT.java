package com.carddemo.trantype.it;

import com.carddemo.trantype.Messages;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end against PostgreSQL 15 with the Flyway migrations (V1 schema, V2 CREADB21 seed). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = "carddemo.db.lock-timeout=1s")
class TransactionTypeApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    private String adminToken;

    @BeforeEach
    void resetData() throws Exception {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        jdbc.execute("DELETE FROM transaction_type_category");
        jdbc.execute("DELETE FROM transaction_type");
        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V2__seed_data.sql"));
        }
        adminToken = login("ADMIN001", "PASSWORD");
    }

    // ------------------------------------------------------------------ helpers

    private String login(String user, String password) {
        ResponseEntity<JsonNode> r = rest.postForEntity("/api/auth/login",
                Map.of("userId", user, "password", password), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody().get("token").asText();
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String url, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> admin(HttpMethod method, String url, Object body) {
        return call(method, url, body, adminToken);
    }

    private static List<String> keys(JsonNode page) {
        List<String> keys = new ArrayList<>();
        page.get("rows").forEach(row -> keys.add(row.get("trType").asText()));
        return keys;
    }

    private void addTypes(int from, int to) {
        for (int i = from; i <= to; i++) {
            jdbc.update("INSERT INTO transaction_type VALUES (?, ?)", String.format("%02d", i), "EXTRA TYPE " + i);
        }
    }

    // ------------------------------------------------------------------ schema / seed

    @Test
    void flywaySeedMatchesCreadb21() { // REQ-NFR-007
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_type", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_type_category", Integer.class))
                .isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT tr_description FROM transaction_type WHERE tr_type = '06'",
                String.class)).isEqualTo("REVERAL");
    }

    // ------------------------------------------------------------------ paging

    @Test
    void firstPageOfSeedIsSingleFullPage() { // REQ-CTLI-001, REQ-EDGE-002
        JsonNode page = admin(HttpMethod.GET, "/api/transaction-types", null).getBody();

        assertThat(keys(page)).containsExactly("01", "02", "03", "04", "05", "06", "07");
        assertThat(page.get("hasNext").asBoolean()).isFalse();
        assertThat(page.get("hasPrev").asBoolean()).isFalse();
        assertThat(page.get("pageNumber").asInt()).isEqualTo(1);
        assertThat(page.get("infoMessage").asText()).isEqualTo(Messages.INFO_REC_ACTIONS);
    }

    @Test
    void pageForwardAndBackwardAcrossBoundaries() { // REQ-CTLI-002..004, REQ-EDGE-003
        addTypes(8, 16); // 16 rows -> pages of 7, 7, 2

        JsonNode p1 = admin(HttpMethod.GET, "/api/transaction-types?size=7", null).getBody();
        assertThat(p1.get("hasNext").asBoolean()).isTrue();
        assertThat(p1.get("nextCursor").asText()).isEqualTo("08");

        JsonNode p2 = admin(HttpMethod.GET, "/api/transaction-types?direction=next&cursor="
                + p1.get("nextCursor").asText(), null).getBody();
        assertThat(keys(p2)).containsExactly("08", "09", "10", "11", "12", "13", "14");
        assertThat(p2.get("pageNumber").asInt()).isEqualTo(2);
        assertThat(p2.get("hasPrev").asBoolean()).isTrue();

        JsonNode p3 = admin(HttpMethod.GET, "/api/transaction-types?direction=next&cursor="
                + p2.get("nextCursor").asText(), null).getBody();
        assertThat(keys(p3)).containsExactly("15", "16");
        assertThat(p3.get("hasNext").asBoolean()).isFalse();
        assertThat(p3.get("pageNumber").asInt()).isEqualTo(3);

        JsonNode back = admin(HttpMethod.GET, "/api/transaction-types?direction=prev&cursor="
                + p3.get("firstKey").asText(), null).getBody();
        assertThat(keys(back)).isEqualTo(keys(p2));
        assertThat(back.get("pageNumber").asInt()).isEqualTo(2);
        assertThat(back.get("nextCursor").asText()).isEqualTo("15");

        JsonNode first = admin(HttpMethod.GET, "/api/transaction-types?direction=prev&cursor="
                + back.get("firstKey").asText(), null).getBody();
        assertThat(keys(first)).isEqualTo(keys(p1));
        assertThat(first.get("hasPrev").asBoolean()).isFalse();
    }

    @Test
    void prevOnFirstPage() { // REQ-CTLI-005
        JsonNode page = admin(HttpMethod.GET, "/api/transaction-types?direction=prev&cursor=01", null).getBody();

        assertThat(page.get("errorMessage").asText()).isEqualTo(Messages.NO_PREVIOUS_PAGES);
        assertThat(page.get("pageNumber").asInt()).isEqualTo(1);
    }

    @Test
    void shortBackwardPageAfterDeletesReturnsFirstPage() { // REQ-EDGE-004
        addTypes(8, 9);
        jdbc.update("DELETE FROM transaction_type_category WHERE trc_type_code IN ('02','03')");
        jdbc.update("DELETE FROM transaction_type WHERE tr_type IN ('02','03')");

        JsonNode page = admin(HttpMethod.GET, "/api/transaction-types?direction=prev&cursor=08", null).getBody();

        assertThat(keys(page)).containsExactly("01", "04", "05", "06", "07", "08", "09");
        assertThat(page.get("pageNumber").asInt()).isEqualTo(1);
    }

    @Test
    void emptyTable() { // REQ-CTLI-007, REQ-EDGE-001
        jdbc.execute("DELETE FROM transaction_type_category");
        jdbc.execute("DELETE FROM transaction_type");

        JsonNode page = admin(HttpMethod.GET, "/api/transaction-types", null).getBody();

        assertThat(page.get("rows").size()).isZero();
        assertThat(page.get("hasNext").asBoolean()).isFalse();
        assertThat(page.get("errorMessage").asText()).isEqualTo(Messages.NO_RECORDS_FOUND);
    }

    // ------------------------------------------------------------------ filters

    @Test
    void typeFilterExactMatch() { // REQ-CTLI-009
        JsonNode page = admin(HttpMethod.GET, "/api/transaction-types?typeFilter=03", null).getBody();
        assertThat(keys(page)).containsExactly("03");
    }

    @Test
    void invalidTypeFilter() { // REQ-CTLI-010
        ResponseEntity<JsonNode> r = admin(HttpMethod.GET, "/api/transaction-types?typeFilter=3X", null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("message").asText()).isEqualTo(Messages.TYPE_FILTER_INVALID);
    }

    @Test
    void descriptionFilterLike() { // REQ-CTLI-011
        JsonNode page = admin(HttpMethod.GET, "/api/transaction-types?descFilter=RE", null).getBody();
        assertThat(keys(page)).containsExactly("03", "05", "06"); // CREDIT, REFUND, REVERAL
    }

    @Test
    void filtersWithNoMatches() { // REQ-CTLI-012
        JsonNode page = admin(HttpMethod.GET, "/api/transaction-types?typeFilter=01&descFilter=PAY", null)
                .getBody();

        assertThat(page.get("rows").size()).isZero();
        assertThat(page.get("errorMessage").asText()).isEqualTo(Messages.NO_RECORDS_FOR_FILTERS);
    }

    // ------------------------------------------------------------------ read

    @Test
    void getExistingNormalizesKey() { // REQ-CTTU-005, REQ-CTTU-006
        ResponseEntity<JsonNode> r = admin(HttpMethod.GET, "/api/transaction-types/2", null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("trType").asText()).isEqualTo("02");
        assertThat(r.getBody().get("trDescription").asText()).isEqualTo("PAYMENT");
    }

    @Test
    void getMissingKey() { // REQ-CTTU-007
        ResponseEntity<JsonNode> r = admin(HttpMethod.GET, "/api/transaction-types/42", null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().get("message").asText()).isEqualTo("No record found for this key in database");
    }

    @Test
    void getInvalidKeys() { // REQ-CTTU-003, REQ-CTTU-004
        assertThat(admin(HttpMethod.GET, "/api/transaction-types/AB", null).getBody().get("message").asText())
                .isEqualTo(Messages.CODE_NUMERIC);
        assertThat(admin(HttpMethod.GET, "/api/transaction-types/00", null).getBody().get("message").asText())
                .isEqualTo(Messages.CODE_NOT_ZERO);
    }

    // ------------------------------------------------------------------ add / edit

    @Test
    void addValidThenDuplicate() { // REQ-CTTU-013, REQ-CTTU-014, REQ-EDGE-007
        ResponseEntity<JsonNode> created = admin(HttpMethod.POST, "/api/transaction-types",
                Map.of("trType", "8", "trDescription", " FEE ASSESSMENT "));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation().getPath()).isEqualTo("/api/transaction-types/08");
        assertThat(jdbc.queryForObject("SELECT tr_description FROM transaction_type WHERE tr_type='08'",
                String.class)).isEqualTo("FEE ASSESSMENT");

        ResponseEntity<JsonNode> dup = admin(HttpMethod.POST, "/api/transaction-types",
                Map.of("trType", "08", "trDescription", "AGAIN"));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody().get("code").asText()).isEqualTo("DUPLICATE_KEY");
        assertThat(dup.getBody().get("message").asText())
                .isEqualTo("Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803");
    }

    @Test
    void addInvalidDescription() { // REQ-CTTU-010, REQ-EDGE-009
        ResponseEntity<JsonNode> r = admin(HttpMethod.POST, "/api/transaction-types",
                Map.of("trType", "08", "trDescription", "FEE-ASSESSMENT"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("message").asText()).isEqualTo(Messages.DESC_ALPHANUM);
    }

    @Test
    void inlineUpdateValidAndInvalid() { // REQ-CTLI-021, REQ-CTLI-023, REQ-EDGE-015
        ResponseEntity<JsonNode> ok = admin(HttpMethod.PUT, "/api/transaction-types/06",
                Map.of("trDescription", "REVERSAL  "));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT tr_description FROM transaction_type WHERE tr_type='06'",
                String.class)).isEqualTo("REVERSAL");

        ResponseEntity<JsonNode> blank = admin(HttpMethod.PUT, "/api/transaction-types/06",
                Map.of("trDescription", "  "));
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody().get("message").asText()).isEqualTo(Messages.DESC_REQUIRED);

        ResponseEntity<JsonNode> same = admin(HttpMethod.PUT, "/api/transaction-types/06",
                Map.of("trDescription", "reversal"));
        assertThat(same.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(same.getBody().get("code").asText()).isEqualTo("NO_CHANGE");
        assertThat(same.getBody().get("message").asText()).isEqualTo(Messages.NO_CHANGE_DATABASE);
    }

    @Test
    void updateMissingKey() { // REQ-CTLI-024, REQ-EDGE-014
        ResponseEntity<JsonNode> r = admin(HttpMethod.PUT, "/api/transaction-types/42",
                Map.of("trDescription", "NOPE"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().get("message").asText()).startsWith("Record not found. Deleted by others ?");
    }

    @Test
    void upsertInsertsMissingKey() { // REQ-CTTU-013 (UPDATE then INSERT on +100)
        ResponseEntity<JsonNode> r = admin(HttpMethod.PUT, "/api/transaction-types/42?upsert=true",
                Map.of("trDescription", "ANSWER"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_type WHERE tr_type='42'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void updateBlockedByRowLock() throws Exception { // REQ-CTLI-025, REQ-CTTU-015
        try (Connection locker = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            locker.createStatement().execute("SELECT * FROM transaction_type WHERE tr_type = '05' FOR UPDATE");

            ResponseEntity<JsonNode> ctli = admin(HttpMethod.PUT, "/api/transaction-types/05",
                    Map.of("trDescription", "REFUNDS"));
            ResponseEntity<JsonNode> cttu = admin(HttpMethod.PUT, "/api/transaction-types/05?upsert=true",
                    Map.of("trDescription", "REFUNDS"));
            locker.rollback();

            assertThat(ctli.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(ctli.getBody().get("code").asText()).isEqualTo("LOCK_CONFLICT");
            assertThat(ctli.getBody().get("message").asText()).isEqualTo(Messages.UPDATE_DEADLOCK);
            assertThat(cttu.getBody().get("message").asText()).isEqualTo(Messages.COULD_NOT_LOCK);
        }
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteWithChildrenIsRestricted() { // REQ-CTLI-018, REQ-CTTU-019, REQ-EDGE-005
        ResponseEntity<JsonNode> r = admin(HttpMethod.DELETE, "/api/transaction-types/01", null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().get("code").asText()).isEqualTo("FK_RESTRICT");
        assertThat(r.getBody().get("message").asText()).startsWith("Please delete associated child records first:");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_type WHERE tr_type='01'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_type_category WHERE trc_type_code='01'",
                Integer.class)).isEqualTo(5);
    }

    @Test
    void deleteWithoutChildren() { // REQ-CTLI-017, REQ-CTTU-018, REQ-EDGE-006
        addTypes(9, 9);

        assertThat(admin(HttpMethod.DELETE, "/api/transaction-types/9", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_type WHERE tr_type='09'", Integer.class))
                .isZero();
        ResponseEntity<JsonNode> again = admin(HttpMethod.DELETE, "/api/transaction-types/09", null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ security

    @Test
    void nonAdminRejected() { // REQ-EDGE-012
        String userToken = login("USER0001", "PASSWORD");

        ResponseEntity<JsonNode> r = call(HttpMethod.GET, "/api/transaction-types", null, userToken);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().get("message").asText()).isEqualTo("No access - Admin Only option... ");
    }

    @Test
    void unauthenticatedRejected() { // REQ-EDGE-013
        assertThat(call(HttpMethod.GET, "/api/transaction-types", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/transaction-types", null, "not-a-jwt").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginMessages() {
        ResponseEntity<JsonNode> wrong = rest.postForEntity("/api/auth/login",
                Map.of("userId", "ADMIN001", "password", "nope"), JsonNode.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody().get("message").asText()).isEqualTo(Messages.WRONG_PASSWORD);

        ResponseEntity<JsonNode> lower = rest.postForEntity("/api/auth/login",
                Map.of("userId", "admin001", "password", "password"), JsonNode.class);
        assertThat(lower.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lower.getBody().get("userType").asText()).isEqualTo("A");
    }
}
