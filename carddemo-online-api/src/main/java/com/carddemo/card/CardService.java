package com.carddemo.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CardService {
    public static final String ACTIONS = "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD";
    public static final String ACCOUNT_ERROR = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    public static final String CARD_ERROR = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    public static final String EMPTY = "NO RECORDS FOUND FOR THIS SEARCH CONDITION.";
    private final JdbcTemplate jdbc;

    public CardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Row(String accountId, String cardNumber, String activeStatus) { }
    public record Detail(String accountId, String cardNumber, String embossedName,
                         String expirationMonth, String expirationYear, String activeStatus) { }
    public record Page(List<Row> rows, String firstKey, String nextCursor, int pageNumber,
                       boolean lastPageShown, String message, String info) { }
    public record Selection(String action, String accountId, String cardNumber) { }

    public static class CardError extends RuntimeException {
        private final HttpStatus status;
        CardError(HttpStatus status, String message) { super(message); this.status = status; }
        public HttpStatus status() { return status; }
    }

    private static CardError invalid(String message) { return new CardError(HttpStatus.BAD_REQUEST, message); }

    private static String input(String value) { return value == null ? "" : value.stripTrailing(); }

    private static boolean blank(String value, int width) { return value.isEmpty() || value.equals("0".repeat(width)); }

    private static String validate(String value, int width, String message) {
        value = input(value);
        if (blank(value, width)) return "";
        if (!value.matches("[0-9]{" + width + "}")) throw invalid(message);
        return value;
    }

    public Page list(String accountId, String cardNumber, String cursor, String firstKey, String key,
                     int pageNumber, boolean lastPageShown) {
        accountId = validate(accountId, 11, ACCOUNT_ERROR);
        cardNumber = validate(cardNumber, 16, CARD_ERROR);
        cursor = validate(cursor, 16, CARD_ERROR);
        firstKey = validate(firstKey, 16, CARD_ERROR);
        if (!List.of("PF7", "PF8", "RETURN").contains(key)) key = "ENTER";
        int page = Math.max(1, pageNumber);
        boolean backwards = key.equals("PF7") && page > 1;
        boolean bottom = key.equals("PF8") && cursor.isEmpty();
        String boundary = key.equals("PF7") && page == 1 ? "NO PREVIOUS PAGES TO DISPLAY"
                : bottom && lastPageShown ? "NO MORE PAGES TO DISPLAY" : null;
        if (key.equals("ENTER")) { cursor = ""; page = 1; }
        else if (backwards) page--;
        else if (key.equals("PF8") && !bottom) page++;
        if (bottom) cursor = firstKey;

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!accountId.isEmpty()) { where.append(" AND account_id = ?::numeric"); args.add(accountId); }
        if (!cardNumber.isEmpty()) { where.append(" AND card_number = ?"); args.add(cardNumber); }
        if (!cursor.isEmpty()) {
            where.append(backwards ? " AND card_number < ?" : " AND card_number >= ?"); args.add(cursor);
        }
        var rows = new ArrayList<>(jdbc.query("SELECT account_id, card_number, active_status FROM card"
                        + where + " ORDER BY card_number " + (backwards ? "DESC" : "ASC") + " LIMIT 7",
                (rs, n) -> new Row(String.format("%011d", rs.getLong(1)),
                        rs.getString(2).stripTrailing(), rs.getString(3).stripTrailing()), args.toArray()));
        if (backwards) Collections.reverse(rows);
        String next = null;
        if (backwards) next = cursor;
        else if (rows.size() == 7) {
            var lookahead = jdbc.queryForList("SELECT card_number FROM card WHERE card_number > ? ORDER BY card_number LIMIT 1",
                    String.class, rows.get(6).cardNumber());
            if (!lookahead.isEmpty()) next = lookahead.get(0).stripTrailing();
        }
        String message = boundary;
        if (message == null && !bottom && next == null) message = rows.isEmpty() && page == 1 ? EMPTY : "NO MORE RECORDS TO SHOW";
        return new Page(rows, rows.isEmpty() ? null : rows.get(0).cardNumber(), next, page,
                bottom || (key.equals("PF8") && next == null), message, rows.isEmpty() ? null : ACTIONS);
    }

    public Detail view(String accountId, String cardNumber) {
        String account = input(accountId);
        String card = input(cardNumber);
        if (blank(account, 11) && blank(card, 16)) throw invalid("No input received");
        if (blank(account, 11)) throw invalid("Account number not provided");
        validate(account, 11, ACCOUNT_ERROR);
        if (blank(card, 16)) throw invalid("Card number not provided");
        validate(card, 16, CARD_ERROR);
        return jdbc.query("SELECT embossed_name, expiration_date, active_status FROM card WHERE card_number = ?",
                (rs, n) -> {
                    String expiry = rs.getString(2);
                    return new Detail(account, card, rs.getString(1).stripTrailing(),
                            expiry.substring(5, 7), expiry.substring(0, 4), rs.getString(3).stripTrailing());
                }, card).stream().findFirst().orElseThrow(() -> new CardError(HttpStatus.NOT_FOUND,
                        "Did not find cards for this search condition"));
    }

    public Map<String, String> select(List<Selection> selections) {
        if (selections == null || selections.size() > 7) throw invalid("INVALID ACTION CODE");
        long actions = selections.stream().filter(s -> s != null && ("S".equals(s.action()) || "U".equals(s.action()))).count();
        if (actions > 1) throw invalid("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE");
        Selection selected = null;
        for (var s : selections) {
            if (s == null || s.action() == null || s.action().isBlank()) continue;
            if (!List.of("S", "U").contains(s.action())) throw invalid("INVALID ACTION CODE");
            selected = s;
        }
        if (selected == null) return Map.of("message", ACTIONS, "severity", "info");
        if (selected.action().equals("U")) return Map.of("message", "This option is not installed ...", "severity", "info");
        var card = view(selected.accountId(), selected.cardNumber());
        return Map.of("route", "/cards/view", "accountId", card.accountId(), "cardNumber", card.cardNumber(),
                "program", "COCRDSLC", "tranId", "CCDL");
    }
}
