package com.carddemo.card;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CardUpdateService {
    public record Fields(String embossedName, String activeStatus, String expirationMonth, String expirationYear) { }
    public record Screen(String token, String accountId, String cardNumber, Fields fields, String message, boolean confirm) { }
    private record State(String owner, String account, String card, Fields old, String date, int cvv,
                         Fields pending, Instant expires) { }
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    public CardUpdateService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static void error(String message) { throw new CardService.CardError(HttpStatus.BAD_REQUEST, message); }
    private static String text(String value) { return value == null ? "" : value.stripTrailing().toUpperCase(Locale.ROOT); }

    public Screen fetch(String owner, String account, String card) {
        account = text(account); card = text(card);
        if (account.equals("*")) account = "";
        if (card.equals("*")) card = "";
        if ((account.isEmpty() || account.equals("00000000000")) && (card.isEmpty() || card.equals("0000000000000000"))) error("No input received");
        if (account.isEmpty() || account.equals("00000000000")) error("Account number not provided");
        if (!account.matches("[0-9]{11}")) error(CardService.ACCOUNT_ERROR);
        if (card.isEmpty() || card.equals("0000000000000000")) error("Card number not provided");
        if (!card.matches("[0-9]{16}")) error(CardService.CARD_ERROR);
        final String a = account, c = card;
        var rows = jdbc.query("SELECT embossed_name, active_status, expiration_date, card_verification_value FROM card WHERE card_number = ?",
                (rs, n) -> new State(owner, a, c, new Fields(text(rs.getString(1)), text(rs.getString(2)),
                        rs.getString(3).substring(5, 7), rs.getString(3).substring(0, 4)), rs.getString(3), rs.getInt(4), null,
                        Instant.now().plusSeconds(1800)), card);
        if (rows.isEmpty()) error("Did not find cards for this search condition");
        states.entrySet().removeIf(e -> e.getValue().expires().isBefore(Instant.now()));
        String token = UUID.randomUUID().toString();
        states.put(token, rows.get(0));
        return screen(token, rows.get(0), rows.get(0).old(), "Details of selected card shown above", false);
    }

    private Screen screen(String token, State s, Fields fields, String message, boolean confirm) {
        return new Screen(token, s.account(), s.card(), fields, message, confirm);
    }

    public synchronized Screen act(String owner, String token, Fields fields, String key) {
        State s = token == null ? null : states.get(token);
        if (s == null || !s.owner().equals(owner) || s.expires().isBefore(Instant.now())) {
            error("Please enter Account and Card Number");
        }
        if ("PF12".equals(key)) { states.remove(token); return fetch(owner, s.account(), s.card()); }
        if (s.pending() != null) {
            if (!"PF5".equals(key)) return screen(token, s, s.pending(), "Changes validated.Press F5 to save", true);
            Fields p = s.pending();
            int count;
            try {
                count = jdbc.update("""
                    UPDATE card SET account_id = ?::numeric, embossed_name = ?, active_status = ?, expiration_date = ?, filler = ''
                    WHERE card_number = ? AND upper(rtrim(embossed_name)) = ? AND active_status = ?
                    AND expiration_date = ? AND card_verification_value = ?
                    """, s.account(), p.embossedName(), p.activeStatus(), p.expirationYear() + "-" + p.expirationMonth() + s.date().substring(7),
                    s.card(), s.old().embossedName(), s.old().activeStatus(), s.date(), s.cvv());
            } catch (CannotAcquireLockException error) {
                error("Could not lock record for update");
                return null;
            }
            states.remove(token);
            Screen refreshed = fetch(owner, s.account(), s.card());
            return new Screen(refreshed.token(), refreshed.accountId(), refreshed.cardNumber(), refreshed.fields(),
                    count == 1 ? "Changes committed to database" : "Record changed by some one else. Please review", false);
        }
        if (fields == null) error("No input received");
        Fields p = new Fields(fields.embossedName() == null ? "" : fields.embossedName().stripTrailing(),
                fields.activeStatus() == null ? "" : fields.activeStatus().stripTrailing(), text(fields.expirationMonth()), text(fields.expirationYear()));
        if (p.embossedName().equalsIgnoreCase(s.old().embossedName()) && p.activeStatus().equalsIgnoreCase(s.old().activeStatus())
                && p.expirationMonth().equals(s.old().expirationMonth()) && p.expirationYear().equals(s.old().expirationYear()))
            return screen(token, s, s.old(), "No change detected with respect to values fetched.", false);
        if (p.embossedName().isBlank() || p.embossedName().equals("*") || p.embossedName().equals("0".repeat(50))) error("Card name not provided");
        if (p.embossedName().length() > 50 || !p.embossedName().matches("[A-Za-z ]+")) error("Card name can only contain alphabets and spaces");
        if (!p.activeStatus().matches("[YN]")) error("Card Active Status must be Y or N");
        if (!p.expirationMonth().matches("0[1-9]|1[0-2]")) error("Card expiry month must be between 1 and 12");
        if (!p.expirationYear().matches("[0-9]{4}") || Integer.parseInt(p.expirationYear()) < 1950 || Integer.parseInt(p.expirationYear()) > 2099) error("Invalid card expiry year");
        states.put(token, new State(owner, s.account(), s.card(), s.old(), s.date(), s.cvv(), p, s.expires()));
        return screen(token, s, p, "Changes validated.Press F5 to save", true);
    }
}
