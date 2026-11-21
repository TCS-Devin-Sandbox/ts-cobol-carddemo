package com.carddemo.card;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class CardDevData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public CardDevData(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        for (int i = 1; i <= 16; i++) {
            jdbc.update("""
                    INSERT INTO card(card_number, account_id, card_verification_value, embossed_name,
                                     expiration_date, active_status, filler)
                    VALUES (?, ?, 123, ?, '2030-12-31', ?, '') ON CONFLICT (card_number) DO NOTHING
                    """, String.format("400000000000%04d", i), i <= 8 ? 10000000001L : 10000000002L,
                    "SYNTHETIC CARDHOLDER " + i, i == 16 ? "N" : "Y");
        }
    }
}
