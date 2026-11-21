package com.carddemo.transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions/add")
public class TransactionAddController {
    private final JdbcTemplate jdbc;
    public TransactionAddController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Result(Map<String, String> fields, String message, String field, boolean added) { }
    static final String[] KEYS = {"transaction_type_code", "transaction_category_code", "source", "description", "amount", "origination_timestamp", "processed_timestamp", "merchant_id", "merchant_name", "merchant_city", "merchant_zip_code"};
    static final String[] LABELS = {"Type CD", "Category CD", "Source", "Description", "Amount", "Orig Date", "Proc Date", "Merchant ID", "Merchant Name", "Merchant City", "Merchant Zip"};
    static final int[] WIDTHS = {2, 4, 10, 60, 12, 10, 10, 9, 30, 25, 10};
    private Result error(Map<String, String> f, String key, String message) { return new Result(f, message, key, false); }
    private boolean numeric(String value, int width) { return value.matches("[0-9]{" + width + "}"); }
    private String displayAmount(BigDecimal amount) {
        return (amount.signum() < 0 ? "-" : "+") + String.format(java.util.Locale.ROOT, "%011.2f", amount.abs().remainder(new BigDecimal("100000000")));
    }

    @PostMapping
    @Transactional
    public Result enter(@RequestBody Map<String, String> input, @RequestParam(defaultValue = "false") boolean copyLast) {
        var f = new LinkedHashMap<String, String>();
        input.forEach((k, v) -> f.put(k, v == null ? "" : v));
        for (String key : KEYS) f.putIfAbsent(key, "");
        String account = f.getOrDefault("account_id", "");
        String card = f.getOrDefault("card_number", "");
        boolean byAccount = !account.isBlank();
        if (!byAccount && card.isBlank()) return error(f, "account_id", "Account or Card Number must be entered...");
        if (!numeric(byAccount ? account : card, byAccount ? 11 : 16)) return error(f, byAccount ? "account_id" : "card_number", byAccount ? "Account ID must be Numeric..." : "Card Number must be Numeric...");
        try {
            var xrefs = jdbc.queryForList("SELECT * FROM card_xref WHERE " + (byAccount ? "account_id" : "card_number") + " = ? ORDER BY card_number LIMIT 1", byAccount ? new BigDecimal(account) : card);
            if (xrefs.isEmpty()) return error(f, byAccount ? "account_id" : "card_number", byAccount ? "Account ID NOT found..." : "Card Number NOT found...");
            f.put("account_id", String.format("%011d", ((Number)xrefs.get(0).get("account_id")).longValue()));
            f.put("card_number", xrefs.get(0).get("card_number").toString().trim());
        } catch (DataAccessException e) { return error(f, byAccount ? "account_id" : "card_number", byAccount ? "Unable to lookup Acct in XREF AIX file..." : "Unable to lookup Card # in XREF file..."); }
        if (copyLast) {
            try {
                var rows = jdbc.queryForList("SELECT * FROM \"transaction\" ORDER BY transaction_id DESC LIMIT 1");
                if (!rows.isEmpty()) {
                    var row = rows.get(0);
                    for (int i = 0; i < KEYS.length; i++) {
                        String value = row.get(KEYS[i]).toString();
                        if (i == 1 || i == 7) value = String.format("%0" + WIDTHS[i] + "d", ((Number)row.get(KEYS[i])).longValue());
                        if (i == 4) value = displayAmount((BigDecimal)row.get(KEYS[i]));
                        f.put(KEYS[i], value.substring(0, Math.min(value.length(), WIDTHS[i])).stripTrailing());
                    }
                } else {
                    for (String key : KEYS) f.put(key, "");
                    f.put("transaction_category_code", "0000");
                    f.put("merchant_id", "000000000");
                    f.put("amount", "+00000000.00");
                }
            } catch (DataAccessException e) { return error(f, "account_id", "Unable to lookup Transaction..."); }
        }
        for (int i = 0; i < KEYS.length; i++) {
            if (f.get(KEYS[i]).isBlank()) return error(f, KEYS[i], LABELS[i] + " can NOT be empty...");
            if (f.get(KEYS[i]).length() > WIDTHS[i]) return error(f, KEYS[i], LABELS[i] + " exceeds screen width...");
        }
        for (int i : new int[]{0, 1}) if (!numeric(f.get(KEYS[i]), WIDTHS[i])) return error(f, KEYS[i], LABELS[i] + " must be Numeric...");
        if (!f.get("amount").matches("[+-][0-9]{8}\\.[0-9]{2}")) return error(f, "amount", "Amount should be in format -99999999.99");
        for (int i : new int[]{5, 6}) if (!f.get(KEYS[i]).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return error(f, KEYS[i], LABELS[i] + " should be in format YYYY-MM-DD");
        f.put("amount", displayAmount(new BigDecimal(f.get("amount"))));
        for (int i : new int[]{5, 6}) {
            try { if (LocalDate.parse(f.get(KEYS[i])).getYear() == 0) throw new DateTimeException("era"); }
            catch (DateTimeException e) { return error(f, KEYS[i], LABELS[i] + " - Not a valid date..."); }
        }
        if (!numeric(f.get("merchant_id"), 9)) return error(f, "merchant_id", "Merchant ID must be Numeric...");
        String confirm = f.getOrDefault("confirmation", "");
        if (!confirm.equalsIgnoreCase("Y")) return error(f, "confirmation", confirm.isBlank() || confirm.equalsIgnoreCase("N") ? "Confirm to add this transaction..." : "Invalid value. Valid values are (Y/N)...");
        String id;
        try {
            var last = jdbc.queryForList("SELECT transaction_id FROM \"transaction\" ORDER BY transaction_id DESC LIMIT 1");
            id = String.format("%016d", (last.isEmpty() ? BigInteger.ZERO : new BigInteger(last.get(0).get("transaction_id").toString().trim())).add(BigInteger.ONE).mod(BigInteger.TEN.pow(16)));
        } catch (DataAccessException | NumberFormatException e) { return error(f, "account_id", "Unable to lookup Transaction..."); }
        try {
            jdbc.update("INSERT INTO \"transaction\" VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')", id, f.get(KEYS[0]), new BigDecimal(f.get(KEYS[1])), f.get(KEYS[2]), f.get(KEYS[3]), new BigDecimal(f.get(KEYS[4])), new BigDecimal(f.get(KEYS[7])), f.get(KEYS[8]), f.get(KEYS[9]), f.get(KEYS[10]), f.get("card_number"), f.get(KEYS[5]), f.get(KEYS[6]));
            return new Result(Map.of(), "Transaction added successfully.  Your Tran ID is " + id + ".", "account_id", true);
        } catch (DuplicateKeyException e) { return error(f, "account_id", "Tran ID already exist..."); }
        catch (DataAccessException e) { return error(f, "account_id", "Unable to Add Transaction..."); }
    }
}
