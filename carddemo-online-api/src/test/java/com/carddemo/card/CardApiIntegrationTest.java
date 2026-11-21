package com.carddemo.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("dev")
class CardApiIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired CardDevData seed;
    private String token;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM card");
        seed.run(null);
        token = login("user", "user123");
    }

    private String login(String id, String password) {
        return (String) rest.postForObject("/api/auth/login", Map.of("userId", id, "password", password), Map.class).get("token");
    }

    private ResponseEntity<Map<String, Object>> call(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), new ParameterizedTypeReference<>() { });
    }

    private Map<String, Object> get(String path) {
        var response = call(HttpMethod.GET, path, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private void error(String path, int status, String message) {
        var response = call(HttpMethod.GET, path, null);
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).containsEntry("message", message);
    }

    private int rowCount(Map<String, Object> page) { return ((List<?>) page.get("rows")).size(); }

    @Test @DisplayName("CCLI 0000-MAIN / 9000-READ-FORWARD / 9100-READ-BACKWARDS / 1400-SETUP-MESSAGE")
    void browseForwardBackwardAndBoundaries() {
        var first = get("/api/cards");
        assertThat(rowCount(first)).isEqualTo(7);
        assertThat(first).containsEntry("firstKey", "4000000000000001").containsEntry("nextCursor", "4000000000000008")
                .containsEntry("pageNumber", 1).containsEntry("info", CardService.ACTIONS).containsEntry("message", null);
        var second = get("/api/cards?key=PF8&pageNumber=1&cursor=4000000000000008");
        assertThat(rowCount(second)).isEqualTo(7);
        assertThat(second).containsEntry("firstKey", "4000000000000008").containsEntry("pageNumber", 2);
        var back = get("/api/cards?key=PF7&pageNumber=2&cursor=4000000000000008");
        assertThat(back).containsEntry("firstKey", first.get("firstKey")).containsEntry("nextCursor", second.get("firstKey"));
        assertThat(back.get("rows")).isEqualTo(first.get("rows"));
        assertThat(get("/api/cards?key=PF7&pageNumber=1&cursor=4000000000000001"))
                .containsEntry("message", "NO PREVIOUS PAGES TO DISPLAY");
        var last = get("/api/cards?key=PF8&pageNumber=2&cursor=4000000000000015");
        assertThat(rowCount(last)).isEqualTo(2);
        assertThat(last).containsEntry("pageNumber", 3).containsEntry("nextCursor", null)
                .containsEntry("lastPageShown", true).containsEntry("message", "NO MORE RECORDS TO SHOW");
        var bottom = get("/api/cards?key=PF8&pageNumber=3&firstKey=4000000000000015&lastPageShown=true");
        assertThat(bottom.get("rows")).isEqualTo(last.get("rows"));
        assertThat(bottom).containsEntry("message", "NO MORE PAGES TO DISPLAY");
    }

    @Test @DisplayName("CCLI 1400-SETUP-MESSAGE: first PF8 at a short initial page only marks last page shown")
    void shortInitialPageBottomRequiresSecondPf8() {
        var first = get("/api/cards?accountId=10000000002&cardNumber=4000000000000016");
        assertThat(first).containsEntry("lastPageShown", false).containsEntry("message", "NO MORE RECORDS TO SHOW");
        var bottom = get("/api/cards?accountId=10000000002&cardNumber=4000000000000016&key=PF8&firstKey=4000000000000016");
        assertThat(bottom).containsEntry("lastPageShown", true).containsEntry("message", null);
    }

    @Test @DisplayName("CCLI 9500-FILTER-RECORDS: unrestricted users, exact AND filters, unfiltered lookahead")
    void filtersAndRawLookahead() {
        var userPage = get("/api/cards?accountId=10000000002");
        token = login("admin", "admin123");
        assertThat(get("/api/cards?accountId=10000000002")).isEqualTo(userPage);
        var exact = get("/api/cards?accountId=10000000001&cardNumber=4000000000000001");
        assertThat(rowCount(exact)).isEqualTo(1);
        assertThat(get("/api/cards?accountId=10000000002&cardNumber=4000000000000001"))
                .containsEntry("message", CardService.EMPTY);
        jdbc.update("DELETE FROM card WHERE card_number='4000000000000008'");
        var seven = get("/api/cards?accountId=10000000001");
        assertThat(rowCount(seven)).isEqualTo(7);
        assertThat(seven).containsEntry("nextCursor", "4000000000000009");
        var emptyNext = get("/api/cards?accountId=10000000001&key=PF8&cursor=4000000000000009&pageNumber=1");
        assertThat(rowCount(emptyNext)).isZero();
        assertThat(emptyNext).containsEntry("message", "NO MORE RECORDS TO SHOW");
    }

    @Test @DisplayName("CCLI 2000-RECEIVE-MAP / 2100-RECEIVE-SCREEN / 2200-EDIT-INPUTS / 2210-EDIT-ACCOUNT / 2220-EDIT-CARD")
    void optionalFiltersAndValidationPriority() {
        for (String value : List.of("abc", "123", "123456789012", "-1234567890")) {
            error("/api/cards?accountId=" + value + "&cardNumber=x", 400, CardService.ACCOUNT_ERROR);
        }
        for (String value : List.of("x", "123", "12345678901234567")) {
            error("/api/cards?cardNumber=" + value, 400, CardService.CARD_ERROR);
        }
        assertThat(rowCount(get("/api/cards?accountId=00000000000&cardNumber=0000000000000000"))).isEqualTo(7);
        assertThat(get("/api/cards?key=PF2&pageNumber=2")).containsEntry("pageNumber", 1);
        jdbc.update("DELETE FROM card");
        assertThat(get("/api/cards")).containsEntry("rows", List.of()).containsEntry("message", CardService.EMPTY);
    }

    private ResponseEntity<Map<String, Object>> select(Object... rows) {
        return call(HttpMethod.POST, "/api/cards/select", Map.of("selections", List.of(rows)));
    }

    private Map<String, String> action(String action) {
        return Map.of("action", action, "accountId", "10000000001", "cardNumber", "4000000000000001");
    }

    @Test @DisplayName("CCLI 2250-EDIT-ARRAY / 0000-MAIN: S, U, blank, multiple and invalid action codes")
    void selections() {
        var selected = select(action("S"));
        assertThat(selected.getStatusCode().value()).isEqualTo(200);
        assertThat(selected.getBody()).containsEntry("route", "/cards/view").containsEntry("tranId", "CCDL");
        assertThat(select(action("U")).getBody()).containsEntry("message", "This option is not installed ...");
        assertThat(select(action(" ")).getBody()).containsEntry("message", CardService.ACTIONS);
        assertThat(select().getBody()).containsEntry("message", CardService.ACTIONS);
        for (String action : List.of("X", "s", "u", "SU")) {
            var invalid = select(action(action));
            assertThat(invalid.getStatusCode().value()).isEqualTo(400);
            assertThat(invalid.getBody()).containsEntry("message", "INVALID ACTION CODE");
        }
        assertThat(select(action("S"), action("U"), action("X")).getBody())
                .containsEntry("message", "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE");
        assertThat(select(Map.of("action", "S", "accountId", "10000000001", "cardNumber", "9999999999999999"))
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test @DisplayName("CCDL 2000-PROCESS-INPUTS / 2100-RECEIVE-MAP / 2200-EDIT-MAP-INPUTS / 2210-EDIT-ACCOUNT / 2220-EDIT-CARD")
    void detailValidation() {
        error("/api/cards/view", 400, "No input received");
        error("/api/cards/view?accountId=00000000000&cardNumber=0000000000000000", 400, "No input received");
        error("/api/cards/view?cardNumber=4000000000000001", 400, "Account number not provided");
        error("/api/cards/view?accountId=x&cardNumber=x", 400, CardService.ACCOUNT_ERROR);
        error("/api/cards/view?accountId=10000000001", 400, "Card number not provided");
        error("/api/cards/view?accountId=10000000001&cardNumber=x", 400, CardService.CARD_ERROR);
        error("/api/cards/view?accountId=10000000001&cardNumber=9999999999999999", 404,
                "Did not find cards for this search condition");
    }

    @Test @DisplayName("CCDL 9000-READ-DATA / 9100-GETCARD-BYACCTCARD / 1200-SETUP-SCREEN-VARS: card-only lookup")
    void detailFieldsAndMismatchedAccountQuirk() {
        var detail = get("/api/cards/view?accountId=99999999999&cardNumber=4000000000000001");
        assertThat(detail).containsEntry("accountId", "99999999999").containsEntry("cardNumber", "4000000000000001")
                .containsEntry("embossedName", "SYNTHETIC CARDHOLDER 1").containsEntry("expirationMonth", "12")
                .containsEntry("expirationYear", "2030").containsEntry("activeStatus", "Y");
        assertThat(detail).doesNotContainKeys("cvvCode", "cardVerificationValue", "filler");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM card_xref", Integer.class)).isZero();
        seed.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM card", Integer.class)).isEqualTo(16);
    }

    @Test @DisplayName("CCLI / CCDL 0000-MAIN: authentication and menu dispatch")
    void authenticationAndMenuRoutes() {
        assertThat(rest.getForEntity("/api/cards", Map.class).getStatusCode().value()).isEqualTo(401);
        assertThat(rest.getForEntity("/api/cards/view", Map.class).getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.POST, "/api/menus/main/select", Map.of("option", "3")).getBody())
                .containsEntry("route", "/cards").containsEntry("tranId", "CCLI");
        assertThat(call(HttpMethod.POST, "/api/menus/main/select", Map.of("option", "4")).getBody())
                .containsEntry("route", "/cards/view").containsEntry("tranId", "CCDL");
    }
}
