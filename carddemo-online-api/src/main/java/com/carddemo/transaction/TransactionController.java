package com.carddemo.transaction;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final JdbcTemplate jdbc;
    public TransactionController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Page(List<Map<String, Object>> rows, int pageNumber, String message) { }
    public record View(Map<String, Object> transaction, String message) { }

    Page backward(String firstKey, int pageNumber) {
        try {
            var rows = new ArrayList<>(jdbc.queryForList("SELECT * FROM \"transaction\" WHERE transaction_id < ? ORDER BY transaction_id DESC LIMIT 11", firstKey));
            boolean top = rows.size() <= 10;
            if (rows.size() > 10) rows.remove(10);
            Collections.reverse(rows);
            return new Page(rows, top ? 1 : Math.max(1, pageNumber - 1), top ? "You have reached the top of the page..." : "");
        } catch (DataAccessException e) {
            return new Page(List.of(), pageNumber, "Unable to lookup transaction...");
        }
    }

    @GetMapping
    public Page list(@RequestParam(defaultValue = "") String startKey,
                     @RequestParam(defaultValue = "ENTER") String direction,
                     @RequestParam(defaultValue = "1") int pageNumber) {
        if (direction.equals("BACKWARD")) return backward(startKey, pageNumber);
        if (!startKey.isBlank() && !startKey.matches("[0-9]{16}")) {
            return new Page(List.of(), 0, "Tran ID must be Numeric ...");
        }
        try {
            var rows = jdbc.queryForList("SELECT * FROM \"transaction\" WHERE transaction_id "
                    + (direction.equals("FORWARD") ? ">" : ">=") + " ? ORDER BY transaction_id LIMIT 11", startKey);
            if (rows.isEmpty()) return new Page(List.of(), 0, "You are at the top of the page...");
            return new Page(rows.subList(0, Math.min(10, rows.size())), direction.equals("FORWARD") ? pageNumber + 1 : 1,
                    rows.size() <= 10 ? "You have reached the bottom of the page..." : "");
        } catch (DataAccessException e) {
            return new Page(List.of(), 0, "Unable to lookup transaction...");
        }
    }

    Page list(String startKey) { return list(startKey, "ENTER", 1); }

    @GetMapping("/view")
    public View view(@RequestParam(defaultValue = "") String transactionId) {
        if (transactionId.isBlank()) return new View(null, "Tran ID can NOT be empty...");
        try {
            var rows = jdbc.queryForList("SELECT * FROM \"transaction\" WHERE transaction_id = ?", transactionId);
            return rows.isEmpty() ? new View(null, "Transaction ID NOT found...") : new View(rows.get(0), "");
        } catch (DataAccessException e) {
            return new View(null, "Unable to lookup Transaction...");
        }
    }
}
