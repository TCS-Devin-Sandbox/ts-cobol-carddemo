package com.carddemo.useradmin;

import static com.carddemo.trantype.service.ApiException.noChange;
import static com.carddemo.trantype.service.ApiException.notFound;
import static com.carddemo.trantype.service.ApiException.validation;

import com.carddemo.trantype.service.ApiException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {
    private static final int PAGE_SIZE = 10;
    private static final String SELECT = "SELECT user_id, first_name, last_name, user_type FROM user_security";
    private static final String KEY = "user_id COLLATE \"C\"";
    private static final String LOOKUP_FAILED = "Unable to lookup User...";
    private static final String UPDATE_FAILED = "Unable to Update User...";
    private static final String NOT_FOUND = "User ID NOT found...";
    private static final RowMapper<User> USER = (rs, row) -> new User(rs.getString("user_id").stripTrailing(),
            rs.getString("first_name").stripTrailing(), rs.getString("last_name").stripTrailing(),
            rs.getString("user_type"));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public UserAdminService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public record User(String userId, String firstName, String lastName, String userType) { }
    public record Edits(String firstName, String lastName, String password, String userType) { }
    public record UserPage(List<User> rows, String firstKey, String lastKey, boolean hasNext, String message) { }
    private record StoredUser(User user, String passwordHash) { }

    public UserPage list(String startId, String after, String before) {
        String start = optionalId(startId);
        String lower = optionalId(after);
        String upper = optionalId(before);
        if (!lower.isEmpty() && !upper.isEmpty()) {
            throw validation("Specify only one browse cursor");
        }
        boolean backwards = !upper.isEmpty();
        String sql = SELECT + " WHERE " + KEY + " >= ?"
                + (backwards ? " AND " + KEY + " < ?" : lower.isEmpty() ? "" : " AND " + KEY + " > ?")
                + " ORDER BY " + KEY + (backwards ? " DESC" : " ASC") + " LIMIT ?";
        Object[] args = backwards ? new Object[]{start, upper, PAGE_SIZE}
                : lower.isEmpty() ? new Object[]{start, PAGE_SIZE + 1}
                : new Object[]{start, lower, PAGE_SIZE + 1};
        List<User> rows = new ArrayList<>(access(LOOKUP_FAILED, () -> jdbc.query(sql, USER, args)));
        boolean hasNext = backwards || rows.size() > PAGE_SIZE;
        if (backwards) Collections.reverse(rows);
        if (rows.size() > PAGE_SIZE) rows.remove(PAGE_SIZE);
        String message = rows.isEmpty() && lower.isEmpty() && !backwards ? "You are at the top of the page..."
                : !hasNext ? "You have reached the bottom of the page..." : null;
        return new UserPage(rows, rows.isEmpty() ? null : rows.get(0).userId(),
                rows.isEmpty() ? null : rows.get(rows.size() - 1).userId(), hasNext, message);
    }

    public User fetch(String input) {
        String id = id(input);
        return access(LOOKUP_FAILED, () -> jdbc.query(SELECT + " WHERE user_id = ?", USER, id))
                .stream().findFirst().orElseThrow(() -> notFound(NOT_FOUND));
    }

    public String add(String input, Edits edits) {
        String first = required("First Name", edits.firstName(), 20, false);
        String last = required("Last Name", edits.lastName(), 20, false);
        String id = id(input);
        String password = required("Password", edits.password(), 8, true);
        String type = userType(edits.userType());
        String hash = encoder.encode(password);
        int count = access("Unable to Add User...", () -> jdbc.update("""
                INSERT INTO user_security (user_id, first_name, last_name, password_hash, user_type, filler)
                VALUES (?, ?, ?, ?, ?, '') ON CONFLICT (user_id) DO NOTHING
                """, id, first, last, hash, type));
        if (count == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_KEY", "User ID already exist...");
        }
        return success(id, "added");
    }

    @Transactional
    public String update(String input, Edits edits) {
        String id = id(input);
        String first = required("First Name", edits.firstName(), 20, false);
        String last = required("Last Name", edits.lastName(), 20, false);
        String password = normalized(edits.password(), true);
        if (!password.isEmpty()) checkWidth("Password", password, 8);
        String type = userType(edits.userType());
        StoredUser stored = access(LOOKUP_FAILED, () -> jdbc.query("""
                SELECT user_id, first_name, last_name, user_type, password_hash
                FROM user_security WHERE user_id = ? FOR UPDATE
                """, (rs, row) -> new StoredUser(USER.mapRow(rs, row), rs.getString("password_hash")), id))
                .stream().findFirst().orElseThrow(() -> notFound(NOT_FOUND));
        boolean samePassword = password.isEmpty() || encoder.matches(password, stored.passwordHash());
        if (samePassword && stored.user().equals(new User(id, first, last, type))) {
            throw noChange("Please modify to update ...");
        }
        String hash = samePassword ? stored.passwordHash() : encoder.encode(password);
        int changed = access(UPDATE_FAILED, () -> jdbc.update("""
                UPDATE user_security SET first_name = ?, last_name = ?, password_hash = ?, user_type = ?
                WHERE user_id = ?
                """, first, last, hash, type, id));
        if (changed == 0) throw notFound(NOT_FOUND);
        return success(id, "updated");
    }

    public String delete(String input) {
        String id = id(input);
        int count = access(UPDATE_FAILED, () -> jdbc.update("DELETE FROM user_security WHERE user_id = ?", id));
        if (count == 0) throw notFound(NOT_FOUND);
        return success(id, "deleted");
    }

    private static String id(String input) {
        return required("User ID", input, 8, true);
    }

    private static String optionalId(String input) {
        String value = normalized(input, true);
        if (!value.isEmpty()) checkWidth("User ID", value, 8);
        return value;
    }

    private static String userType(String input) {
        String type = required("User Type", input, 1, true);
        if (!type.equals("A") && !type.equals("U")) throw validation("User Type must be A or U...");
        return type;
    }

    private static String required(String field, String input, int width, boolean upper) {
        String value = normalized(input, upper);
        if (value.isEmpty()) throw validation(field + " can NOT be empty...");
        checkWidth(field, value, width);
        return value;
    }

    private static String normalized(String input, boolean upper) {
        String value = input == null ? "" : input.stripTrailing();
        return upper ? value.toUpperCase(Locale.ROOT) : value;
    }

    private static void checkWidth(String field, String value, int width) {
        if (value.length() > width) throw validation(field + " can NOT exceed " + width + " characters...");
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw validation(field + " contains invalid characters...");
        }
    }

    private static <T> T access(String message, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DB_ERROR", message);
        }
    }

    private static String success(String id, String action) {
        return "User " + id.split(" ", 2)[0] + " has been " + action + " ...";
    }
}
