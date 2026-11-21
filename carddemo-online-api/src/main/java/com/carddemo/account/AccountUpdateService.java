package com.carddemo.account;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.carddemo.trantype.service.ApiException;

@Service
public class AccountUpdateService {
    static final String CONFLICT = "Record changed by some one else. Please review";
    static final String NO_CHANGE = "No change detected with respect to values fetched.";
    record Field(String key, String column, int length, boolean number) { }
    static final List<Field> ACCOUNT = List.of(
            new Field("activeStatus", "active_status", 1, false),
            new Field("currentBalance", "current_balance", 15, true),
            new Field("creditLimit", "credit_limit", 15, true),
            new Field("cashCreditLimit", "cash_credit_limit", 15, true),
            new Field("openDate", "open_date", 10, false),
            new Field("expirationDate", "expiration_date", 10, false),
            new Field("reissueDate", "reissue_date", 10, false),
            new Field("currentCycleCredit", "current_cycle_credit", 15, true),
            new Field("currentCycleDebit", "current_cycle_debit", 15, true),
            new Field("accountGroupId", "account_group_id", 10, false));
    static final List<Field> CUSTOMER = List.of(
            new Field("firstName", "first_name", 25, false),
            new Field("middleName", "middle_name", 25, false),
            new Field("lastName", "last_name", 25, false),
            new Field("addressLine1", "address_line_1", 50, false),
            new Field("addressLine2", "address_line_2", 50, false),
            new Field("addressLine3", "address_line_3", 50, false),
            new Field("stateCode", "state_code", 2, false),
            new Field("countryCode", "country_code", 3, false),
            new Field("zipCode", "zip_code", 5, false),
            new Field("phoneNumber1", "phone_number_1", 13, false),
            new Field("phoneNumber2", "phone_number_2", 13, false),
            new Field("socialSecurityNumber", "social_security_number", 9, true),
            new Field("governmentIssuedId", "government_issued_id", 20, false),
            new Field("dateOfBirth", "date_of_birth", 10, false),
            new Field("electronicFundsTransferAccountId", "electronic_funds_transfer_account_id", 10, false),
            new Field("primaryCardholderIndicator", "primary_cardholder_indicator", 1, false),
            new Field("ficoCreditScore", "fico_credit_score", 3, true));

    public record Screen(String accountId, String customerId, String version, Map<String, String> fields,
                         String state, String message, String invalidField) { }
    public record Change(String accountId, String version, Map<String, String> fields, boolean confirmed) { }
    private final JdbcClient jdbc;
    private final AccountViewService view;
    private final ObjectMapper mapper;

    AccountUpdateService(JdbcClient jdbc, AccountViewService view, ObjectMapper mapper) {
        this.jdbc = jdbc; this.view = view; this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Screen read(String accountId) {
        String error = keyError(accountId);
        if (error != null) return new Screen(accountId, null, null, Map.of(), "SEARCH", error, "accountId");
        return readDetails(accountId);
    }

    private Screen readDetails(String accountId) {
        return readDetails(accountId, null);
    }

    private Screen readDetails(String accountId, String lockedCustomerId) {
        var details = lockedCustomerId == null ? view.lookup(accountId)
                : view.lookupCustomer(accountId, Long.parseLong(lockedCustomerId));
        if (details.error() != null) {
            String message = details.error().message();
            return new Screen(accountId, null, null, Map.of(), "SEARCH", message, "accountId");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.putAll(mapper.convertValue(details.account(), new TypeReference<Map<String, String>>() { }));
        fields.putAll(mapper.convertValue(details.customer(), new TypeReference<Map<String, String>>() { }));
        fields.remove("customerId");
        fields.replaceAll((key, value) -> key.equals("zipCode") ? value.substring(0, Math.min(5, value.length()))
                : key.startsWith("phoneNumber") ? value.substring(0, Math.min(13, value.length())) : value);
        String customerId = details.customer().customerId();
        return new Screen(accountId, customerId, token(accountId, customerId, false), fields,
                "EDIT", "Update account details presented above.", null);
    }

    @Transactional
    public Screen change(Change change) {
        String error = keyError(change.accountId());
        if (error != null) return new Screen(change.accountId(), null, null, Map.of(), "SEARCH", error, "accountId");
        // Keep the customer association stable while locking and rewriting both masters.
        var customers = jdbc.sql("SELECT customer_id::text FROM card_xref WHERE account_id = ? LIMIT 2 FOR UPDATE")
                .param(Long.parseLong(change.accountId())).query(String.class).list();
        if (customers.size() != 1) throw conflict();
        String customerId = customers.get(0);
        if (change.version() == null || !change.version().equals(token(change.accountId(), customerId, true))) throw conflict();
        Screen old = readDetails(change.accountId(), customerId);
        if (old.version() == null) throw conflict();
        Map<String, String> fields = normalize(change.fields());
        fields.put("countryCode", old.fields().get("countryCode"));
        if (same(old.fields(), fields)) return new Screen(old.accountId(), old.customerId(), old.version(), fields, "EDIT", NO_CHANGE, null);
        var invalid = new AccountUpdateEdits(fields).validate();
        if (invalid != null) return new Screen(old.accountId(), old.customerId(), old.version(), fields,
                "EDIT", invalid.message(), invalid.field());
        if (!change.confirmed()) return new Screen(old.accountId(), old.customerId(), old.version(), fields,
                "CONFIRM", "Changes validated.Press F5 to save", null);
        write("account", "account_id", change.accountId(), ACCOUNT, fields);
        write("customer", "customer_id", customerId, CUSTOMER, fields);
        var saved = readDetails(change.accountId(), customerId);
        return new Screen(saved.accountId(), saved.customerId(), saved.version(), saved.fields(),
                "SAVED", "Changes committed to database", null);
    }

    private String token(String accountId, String customerId, boolean lock) {
        var account = jdbc.sql("SELECT xmin::text FROM account WHERE account_id = ?" + (lock ? " FOR UPDATE" : ""))
                .param(Long.parseLong(accountId)).query(String.class).optional();
        var customer = jdbc.sql("SELECT xmin::text FROM customer WHERE customer_id = ?" + (lock ? " FOR UPDATE" : ""))
                .param(Long.parseLong(customerId)).query(String.class).optional();
        if (account.isEmpty() || customer.isEmpty()) throw conflict();
        return accountId + ":" + Long.parseLong(customerId) + ":" + account.get() + ":" + customer.get();
    }

    private void write(String table, String key, String id, List<Field> definitions, Map<String, String> fields) {
        String assignments = definitions.stream().map(field -> field.column() + " = ?")
                .collect(java.util.stream.Collectors.joining(", "));
        if (table.equals("account")) assignments += ", zip_code = ?";
        var query = jdbc.sql("UPDATE " + table + " SET " + assignments + " WHERE " + key + " = ?");
        for (Field field : definitions) {
            String value = fields.get(field.key());
            if (table.equals("account") && field.key().equals("accountGroupId")) value = "";
            if (field.key().startsWith("phoneNumber")) {
                String phone = String.format("%-13s", value);
                value = "(" + phone.substring(1, 4) + ")" + phone.substring(5, 8) + "-" + phone.substring(9, 13);
            }
            if (field.key().endsWith("Date") || field.key().equals("dateOfBirth")) {
                String date = value.replace("-", "");
                value = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
            }
            query = query.param(field.number() ? AccountUpdateEdits.amount(value) : value);
        }
        if (table.equals("account")) query = query.param(fields.get("accountGroupId"));
        if (query.param(Long.parseLong(id)).update() != 1) throw conflict();
    }

    static Map<String, String> normalize(Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();
        java.util.stream.Stream.concat(ACCOUNT.stream(), CUSTOMER.stream()).forEach(field -> {
            String value = input == null ? "" : Objects.toString(input.get(field.key()), "");
            if (value.trim().equals("*")) value = "";
            result.put(field.key(), value.substring(0, Math.min(value.length(), field.length())));
        });
        return result;
    }

    static boolean same(Map<String, String> before, Map<String, String> after) {
        return before.entrySet().stream().allMatch(entry -> {
            String value = after.get(entry.getKey());
            if (value == null) return false;
            if (entry.getKey().startsWith("phoneNumber")) {
                String oldPhone = String.format("%-13s", entry.getValue());
                String newPhone = String.format("%-13s", value);
                return oldPhone.substring(1, 4).equals(newPhone.substring(1, 4))
                        && oldPhone.substring(5, 8).equals(newPhone.substring(5, 8))
                        && oldPhone.substring(9, 13).equals(newPhone.substring(9, 13));
            }
            if (entry.getKey().endsWith("Date") || entry.getKey().equals("dateOfBirth")) {
                return entry.getValue().replace("-", "").equals(value.replace("-", ""));
            }
            try {
                if (ACCOUNT.stream().anyMatch(field -> field.number() && field.key().equals(entry.getKey()))) {
                    return AccountUpdateEdits.amount(entry.getValue()).compareTo(AccountUpdateEdits.amount(value)) == 0;
                }
            } catch (NumberFormatException ignored) { return false; }
            return entry.getValue().trim().toUpperCase(Locale.ROOT).equals(value.trim().toUpperCase(Locale.ROOT));
        });
    }

    static String keyError(String id) {
        if (id == null || id.matches("\\*? *")) return "No input received";
        if (!id.matches("[0-9]{11}") || id.equals("00000000000")) {
            return "Account Number if supplied must be a 11 digit Non-Zero Number";
        }
        return null;
    }

    private static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, "ACCOUNT_UPDATE_CONFLICT", CONFLICT); }
}
