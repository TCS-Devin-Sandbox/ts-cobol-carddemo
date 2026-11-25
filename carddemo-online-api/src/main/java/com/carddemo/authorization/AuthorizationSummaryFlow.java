package com.carddemo.authorization;

import com.carddemo.authorization.PendingAuthorizations.Account;
import com.carddemo.authorization.PendingAuthorizations.Row;
import com.carddemo.authorization.PendingAuthorizations.Summary;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** COPAUS0C (CPVS): the summary screen and its five-row PAUTDTL1 browser, with CDEMO-CPVS-INFO as {@link State}. */
@Service
public class AuthorizationSummaryFlow {
    static final int PAGE_ROWS = 5;
    static final int MAX_PAGES = 20;
    static final String TOP = "You are already at the top of the page...";
    static final String BOTTOM = "You are already at the bottom of the page...";
    static final String PAGE_LIMIT = "Only " + MAX_PAGES + " pages can be browsed. Press Enter to start again...";
    private static final Pattern ACCOUNT_ID = Pattern.compile("[0-9]{11}");

    /** CDEMO-ACCT-ID plus CDEMO-CPVS-INFO; keys are {@link AuthorizationKey} text, null for LOW-VALUES. */
    public record State(String accountId, int pageNumber, List<String> previousPageKeys, String lastKey,
                        boolean nextPage, List<String> rowKeys) {
        static final State INITIAL = new State(null, 0, List.of(), null, false, List.of());
    }

    /** XCTL to COPAUS1C (CPVD) with the selected PA-AUTHORIZATION-KEY. */
    public record Transfer(String program, String tranId, String accountId, String authorizationKey) { }

    /** {@code rows} null leaves the list on the screen (SEND without ERASE); account/summary null are blank. */
    public record Screen(Account account, Summary summary, List<Row> rows, String message, boolean accountIdInvalid,
                         Transfer transfer, State state) { }

    private final PendingAuthorizations store;

    public AuthorizationSummaryFlow(PendingAuthorizations store) {
        this.store = store;
    }

    /** MAIN-PARA first entry and PROCESS-ENTER-KEY. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Screen enter(String input, List<String> selections, State state) {
        var nav = new Nav(state == null ? State.INITIAL : state);
        nav.pageNumber = 0;
        if (input == null || input.isBlank()) {
            return new Screen(null, null, List.of(), "Please enter Acct Id...", true, null, nav.state());
        }
        if (!ACCOUNT_ID.matcher(input).matches()) {
            return new Screen(null, null, List.of(), "Acct Id must be Numeric ...", true, null, nav.state());
        }
        nav.accountId = input;
        String message = null;
        var picks = selections == null ? List.<String>of() : selections;
        for (int i = 0; i < Math.min(picks.size(), PAGE_ROWS); i++) {
            var flag = picks.get(i);
            if (flag == null || flag.isBlank()) {
                continue;
            }
            var key = i < nav.rowKeys.size() ? nav.rowKeys.get(i) : null;
            if (key != null) {
                if (flag.equals("S") || flag.equals("s")) {
                    return new Screen(null, null, null, null, false,
                            new Transfer("COPAUS1C", "CPVD", input, key), nav.state());
                }
                message = "Invalid selection. Valid value is S";
            }
            break;
        }
        return gather(nav, message);
    }

    /** GATHER-DETAILS: account section, GET-AUTH-SUMMARY, then the first page. */
    private Screen gather(Nav nav, String selectionMessage) {
        var lookup = store.account(nav.accountId);
        var message = lookup.message() != null ? lookup.message() : selectionMessage;
        long account = Long.parseLong(nav.accountId);
        var summary = store.summary(account);
        List<Row> rows = List.of();
        if (summary.isPresent() && !lookup.error()) {
            rows = pageForward(nav, null, false);
        } else {
            nav.rowKeys = new ArrayList<>();
            nav.lastKey = null;
            nav.nextPage = false;
        }
        return new Screen(lookup.account(), summary.orElse(Summary.ABSENT), rows, message, false, null, nav.state());
    }

    /** PROCESS-PF7-KEY: back one entry in CDEMO-CPVS-PAUKEY-PREV-PG. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Screen previous(State state) {
        var nav = new Nav(state);
        if (nav.pageNumber <= 1) {
            return new Screen(null, null, null, TOP, false, null, nav.state());
        }
        nav.pageNumber--;
        var key = AuthorizationKey.parse(nav.previousPageKeys.get(nav.pageNumber - 1));
        return paged(nav, key, true);
    }

    /** PROCESS-PF8-KEY: reposition on CDEMO-CPVS-PAUKEY-LAST and read the next five. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Screen next(State state) {
        var nav = new Nav(state);
        var last = AuthorizationKey.parse(nav.lastKey);
        if (last == null || !nav.nextPage) {
            return new Screen(null, null, null, BOTTOM, false, null, nav.state());
        }
        if (nav.pageNumber >= MAX_PAGES) {
            return new Screen(null, null, null, PAGE_LIMIT, false, null, nav.state());
        }
        return paged(nav, last, false);
    }

    private Screen paged(Nav nav, AuthorizationKey from, boolean inclusive) {
        if (nav.accountId == null || store.summary(Long.parseLong(nav.accountId)).isEmpty()) {
            nav.rowKeys = new ArrayList<>();
            nav.lastKey = null;
            nav.nextPage = false;
            return new Screen(null, null, List.of(), null, false, null, nav.state());
        }
        return new Screen(null, null, pageForward(nav, from, inclusive), null, false, null, nav.state());
    }

    /**
     * PROCESS-PAGE-FORWARD: up to five rows plus one lookahead GNP. The page number is bumped on the first row
     * even when paging back, so PF7 only ever returns one page (legacy behaviour).
     */
    private List<Row> pageForward(Nav nav, AuthorizationKey from, boolean inclusive) {
        var read = store.details(Long.parseLong(nav.accountId), from, inclusive, PAGE_ROWS + 1);
        var rows = read.subList(0, Math.min(PAGE_ROWS, read.size()));
        nav.lastKey = null;
        nav.rowKeys = new ArrayList<>();
        for (var row : rows) {
            nav.rowKeys.add(row.authorizationKey());
            nav.lastKey = row.authorizationKey();
        }
        if (!rows.isEmpty()) {
            nav.pageNumber++;
            while (nav.previousPageKeys.size() < nav.pageNumber) {
                nav.previousPageKeys.add(null);
            }
            nav.previousPageKeys.set(nav.pageNumber - 1, rows.get(0).authorizationKey());
        }
        nav.nextPage = read.size() > PAGE_ROWS;
        return List.copyOf(rows);
    }

    /** Rejects a CDEMO-CPVS-INFO the screen could not have produced. */
    static void validate(State state) {
        if (state == null) {
            return;
        }
        var keys = state.previousPageKeys() == null ? List.<String>of() : state.previousPageKeys();
        var rows = state.rowKeys() == null ? List.<String>of() : state.rowKeys();
        if (state.pageNumber() < 0 || state.pageNumber() > MAX_PAGES || keys.size() > MAX_PAGES
                || keys.size() < state.pageNumber() || rows.size() > PAGE_ROWS
                || (state.accountId() != null && !ACCOUNT_ID.matcher(state.accountId()).matches())) {
            throw new IllegalArgumentException("Invalid CPVS state");
        }
        keys.forEach(AuthorizationKey::parse);
        rows.forEach(AuthorizationKey::parse);
        AuthorizationKey.parse(state.lastKey());
    }

    private static final class Nav {
        String accountId;
        int pageNumber;
        final List<String> previousPageKeys;
        String lastKey;
        boolean nextPage;
        List<String> rowKeys;

        Nav(State state) {
            accountId = state.accountId();
            pageNumber = state.pageNumber();
            previousPageKeys = new ArrayList<>(state.previousPageKeys() == null ? List.of() : state.previousPageKeys());
            lastKey = state.lastKey();
            nextPage = state.nextPage();
            rowKeys = new ArrayList<>(state.rowKeys() == null ? List.of() : state.rowKeys());
        }

        State state() {
            return new State(accountId, pageNumber, List.copyOf(previousPageKeys.stream()
                    .map(k -> k == null ? "" : k).toList()), lastKey, nextPage, List.copyOf(rowKeys));
        }
    }
}
