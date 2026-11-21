package com.carddemo.card;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("dev")
class CardUpdateIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired CardUpdateService service;
    @Autowired JdbcTemplate jdbc;
    final CardUpdateService.Fields changed = new CardUpdateService.Fields("NEW NAME", "N", "02", "1950");
    @BeforeEach void seed() {
        jdbc.update("DELETE FROM card");
        jdbc.update("INSERT INTO card VALUES ('4000000000000001',10000000001,123,'OLD NAME','2030-12-31','Y','')");
    }
    CardUpdateService.Screen fetch() { return service.fetch("user", "10000000001", "4000000000000001"); }
    void error(Runnable call, String message) { assertThatThrownBy(call::run).isInstanceOf(CardService.CardError.class).hasMessage(message); }

    @Test @DisplayName("CCUP 1210-EDIT-ACCOUNT / 1220-EDIT-CARD / 9100-GETCARD-BYACCTCARD")
    void keysAndMissingRecord() {
        error(() -> service.fetch("u", "", ""), "No input received");
        error(() -> service.fetch("u", "00000000000", "4000000000000001"), "Account number not provided");
        error(() -> service.fetch("u", "x", "x"), CardService.ACCOUNT_ERROR);
        error(() -> service.fetch("u", "10000000001", ""), "Card number not provided");
        error(() -> service.fetch("u", "10000000001", "x"), CardService.CARD_ERROR);
        error(() -> service.fetch("u", "10000000001", "9999999999999999"), "Did not find cards for this search condition");
        assertThat(service.fetch("u", "99999999999", "4000000000000001").accountId()).isEqualTo("99999999999");
    }
    @Test @DisplayName("CCUP 1200-EDIT-MAP-INPUTS / 1230-EDIT-NAME / 1240-EDIT-CARDSTATUS / 1250-EDIT-EXPIRY-MON / 1260-EDIT-EXPIRY-YEAR")
    void validationAndNoChanges() {
        var s = fetch();
        assertThat(service.act("user", s.token(), s.fields(), "ENTER").message()).isEqualTo("No change detected with respect to values fetched.");
        String[][] cases = {{"", "Y", "12", "2030", "Card name not provided"},
                {"BAD1", "Y", "12", "2030", "Card name can only contain alphabets and spaces"},
                {"NAME", "X", "12", "2030", "Card Active Status must be Y or N"},
                {"NAME", "Y", "13", "2030", "Card expiry month must be between 1 and 12"},
                {"NAME", "Y", "12", "1949", "Invalid card expiry year"},
                {"NAME", "Y", "12", "2100", "Invalid card expiry year"}};
        for (var c : cases) error(() -> service.act("user", s.token(), new CardUpdateService.Fields(c[0], c[1], c[2], c[3]), "ENTER"), c[4]);
    }
    @Test @DisplayName("CCUP 2000-DECIDE-ACTION / 9200-WRITE-PROCESSING: F5 confirmation, PF12 cancellation")
    void confirmationAndCancel() {
        var s = fetch();
        assertThat(service.act("user", s.token(), changed, "ENTER").confirm()).isTrue();
        assertThat(jdbc.queryForObject("SELECT rtrim(embossed_name) FROM card", String.class)).isEqualTo("OLD NAME");
        assertThat(service.act("user", s.token(), null, "ENTER").confirm()).isTrue();
        assertThat(service.act("user", s.token(), null, "PF5").message()).isEqualTo("Changes committed to database");
        assertThat(jdbc.queryForObject("SELECT expiration_date FROM card", String.class)).isEqualTo("1950-02-31");
        var again = fetch();
        service.act("user", again.token(), new CardUpdateService.Fields("OTHER NAME", "Y", "12", "2099"), "ENTER");
        assertThat(service.act("user", again.token(), null, "PF12").fields()).isEqualTo(changed);
        error(() -> service.act("other", fetch().token(), changed, "ENTER"), "Please enter Account and Card Number");
    }
    @Test @DisplayName("CCUP 9300-CHECK-CHANGE-IN-REC: concurrent modification between fetch and F5")
    void concurrentModification() {
        var s = fetch();
        service.act("user", s.token(), changed, "ENTER");
        jdbc.update("UPDATE card SET card_verification_value=456, embossed_name='SOMEONE ELSE'");
        var result = service.act("user", s.token(), null, "PF5");
        assertThat(result.message()).isEqualTo("Record changed by some one else. Please review");
        assertThat(result.fields().embossedName()).isEqualTo("SOMEONE ELSE");
        assertThat(jdbc.queryForObject("SELECT card_verification_value FROM card", Integer.class)).isEqualTo(456);
    }
}
