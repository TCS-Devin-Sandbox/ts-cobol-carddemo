package com.carddemo.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.authorization.AuthorizationSummaryFlow.State;
import com.carddemo.authorization.AuthorizationSummaryFlow.Transfer;
import com.carddemo.authorization.PendingAuthorizations.Account;
import com.carddemo.authorization.PendingAuthorizations.AccountLookup;
import com.carddemo.authorization.PendingAuthorizations.Row;
import com.carddemo.authorization.PendingAuthorizations.Summary;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** COPAUS0C paragraph behaviour over an in-memory PAUTSUM0/PAUTDTL1 store, keyed in PAUT9CTS order. */
class AuthorizationSummaryFlowTest {
    private static final String ACCOUNT = "00000000042";
    private static final Account SECTION = new Account("000000007", "Example Q Fixture", "123 Sample St,Unit 2",
            "Sample City,TS,01234", "(000)000-0000", new BigDecimal("9000.00"), new BigDecimal("300.00"));
    private static final Summary SUMMARY = new Summary(true, 3, 1, new BigDecimal("10.00"), BigDecimal.ZERO,
            new BigDecimal("30.00"), new BigDecimal("5.00"));

    static final class Store implements PendingAuthorizations {
        final List<Row> rows = new ArrayList<>();
        AccountLookup lookup = new AccountLookup(SECTION, null, false);
        boolean summary = true;

        Store(int count) {
            IntStream.range(0, count).forEach(i -> rows.add(new Row(new AuthorizationKey(1000, i).toString(),
                    "TX" + i, "01/02/26", "10:00:00", "0100", "A", "P", BigDecimal.ONE)));
        }

        @Override public AccountLookup account(String accountId) { return lookup; }

        @Override public Optional<Summary> summary(long accountId) {
            return summary ? Optional.of(SUMMARY) : Optional.empty();
        }

        @Override public List<Row> details(long accountId, AuthorizationKey from, boolean inclusive, int limit) {
            return rows.stream().filter(r -> from == null || (inclusive
                            ? r.authorizationKey().compareTo(from.toString()) >= 0
                            : r.authorizationKey().compareTo(from.toString()) > 0))
                    .limit(limit).toList();
        }
    }

    private static String key(int i) {
        return new AuthorizationKey(1000, i).toString();
    }

    private static List<String> ids(AuthorizationSummaryFlow.Screen screen) {
        return screen.rows().stream().map(Row::transactionId).toList();
    }

    @Test
    void PROCESS_ENTER_KEY_blankAndNonNumericAccountIds() {
        var flow = new AuthorizationSummaryFlow(new Store(3));
        for (var blank : Arrays.asList(null, "", "           ")) {
            var screen = flow.enter(blank, List.of(), State.INITIAL);
            assertThat(screen.message()).isEqualTo("Please enter Acct Id...");
            assertThat(screen.accountIdInvalid()).isTrue();
            assertThat(screen.rows()).isEmpty();
        }
        for (var bad : List.of("42", "0000000004X", "00000000042 ")) {
            assertThat(flow.enter(bad, List.of(), State.INITIAL).message()).isEqualTo("Acct Id must be Numeric ...");
        }
    }

    @Test
    void GATHER_DETAILS_firstPageNewestFirstWithLookahead() {
        var screen = new AuthorizationSummaryFlow(new Store(7)).enter(ACCOUNT, List.of(), State.INITIAL);
        assertThat(screen.account()).isEqualTo(SECTION);
        assertThat(screen.summary()).isEqualTo(SUMMARY);
        assertThat(ids(screen)).containsExactly("TX0", "TX1", "TX2", "TX3", "TX4");
        assertThat(screen.message()).isNull();
        assertThat(screen.state()).isEqualTo(new State(ACCOUNT, 1, List.of(key(0)), key(4), true,
                List.of(key(0), key(1), key(2), key(3), key(4))));
    }

    @Test
    void PROCESS_PAGE_FORWARD_exactlyFiveRowsHasNoNextPage() {
        var screen = new AuthorizationSummaryFlow(new Store(5)).enter(ACCOUNT, List.of(), State.INITIAL);
        assertThat(screen.rows()).hasSize(5);
        assertThat(screen.state().nextPage()).isFalse();
    }

    @Test
    void GET_AUTH_SUMMARY_missingRootShowsZeroCountsAndNoList() {
        var store = new Store(3);
        store.summary = false;
        var screen = new AuthorizationSummaryFlow(store).enter(ACCOUNT, List.of(), State.INITIAL);
        assertThat(screen.summary()).isEqualTo(Summary.ABSENT);
        assertThat(screen.rows()).isEmpty();
        assertThat(screen.state().pageNumber()).isZero();
    }

    @Test
    void GATHER_ACCOUNT_DETAILS_messageReplacesSelectionMessageAndSystemErrorSkipsList() {
        var store = new Store(3);
        store.lookup = new AccountLookup(null, "Account:00000000042 not found in XREF file.", false);
        var flow = new AuthorizationSummaryFlow(store);
        var first = flow.enter(ACCOUNT, List.of(), State.INITIAL);
        var screen = flow.enter(ACCOUNT, List.of("X"), first.state());
        assertThat(screen.message()).isEqualTo("Account:00000000042 not found in XREF file.");
        assertThat(screen.rows()).hasSize(3);
        store.lookup = new AccountLookup(null, "system error", true);
        var failed = flow.enter(ACCOUNT, List.of(), State.INITIAL);
        assertThat(failed.summary()).isEqualTo(SUMMARY);
        assertThat(failed.rows()).isEmpty();
    }

    @Test
    void PROCESS_PF8_KEY_pagesForwardThenStopsAtBottom() {
        var flow = new AuthorizationSummaryFlow(new Store(12));
        var page1 = flow.enter(ACCOUNT, List.of(), State.INITIAL);
        var page2 = flow.next(page1.state());
        assertThat(ids(page2)).containsExactly("TX5", "TX6", "TX7", "TX8", "TX9");
        var page3 = flow.next(page2.state());
        assertThat(ids(page3)).containsExactly("TX10", "TX11");
        assertThat(page3.state().pageNumber()).isEqualTo(3);
        assertThat(page3.state().previousPageKeys()).containsExactly(key(0), key(5), key(10));
        var bottom = flow.next(page3.state());
        assertThat(bottom.message()).isEqualTo("You are already at the bottom of the page...");
        assertThat(bottom.rows()).isNull();
        assertThat(bottom.state()).isEqualTo(page3.state());
    }

    @Test
    void PROCESS_PF7_KEY_topOfFirstPage() {
        var flow = new AuthorizationSummaryFlow(new Store(12));
        var page1 = flow.enter(ACCOUNT, List.of(), State.INITIAL);
        var top = flow.previous(page1.state());
        assertThat(top.message()).isEqualTo("You are already at the top of the page...");
        assertThat(top.rows()).isNull();
    }

    @Test
    void PROCESS_PF7_KEY_legacyPageCounterOnlyEverGoesBackOnePage() {
        var flow = new AuthorizationSummaryFlow(new Store(16));
        var page = flow.enter(ACCOUNT, List.of(), State.INITIAL);
        page = flow.next(page.state());
        page = flow.next(page.state());
        assertThat(ids(page)).startsWith("TX10");
        var back = flow.previous(page.state());
        assertThat(ids(back)).startsWith("TX5");
        assertThat(back.state().pageNumber()).isEqualTo(3);
        var again = flow.previous(back.state());
        assertThat(ids(again)).startsWith("TX5");
        assertThat(again.message()).isNull();
        assertThat(flow.next(again.state()).rows()).extracting(Row::transactionId).startsWith("TX10");
    }

    @Test
    void PROCESS_PF8_KEY_boundedAtTwentyPages() {
        var flow = new AuthorizationSummaryFlow(new Store(5 * 21 + 1));
        var page = flow.enter(ACCOUNT, List.of(), State.INITIAL);
        for (int i = 2; i <= 20; i++) {
            page = flow.next(page.state());
            assertThat(page.state().pageNumber()).isEqualTo(i);
        }
        assertThat(page.state().previousPageKeys()).hasSize(20);
        assertThat(page.state().nextPage()).isTrue();
        var limited = flow.next(page.state());
        assertThat(limited.message()).isEqualTo("Only 20 pages can be browsed. Press Enter to start again...");
        assertThat(limited.rows()).isNull();
        assertThat(limited.state()).isEqualTo(page.state());
        var restart = flow.enter(ACCOUNT, List.of(), page.state());
        assertThat(restart.state().pageNumber()).isEqualTo(1);
    }

    @Test
    void PROCESS_ENTER_KEY_selectionTransfersToCpvdOrRejectsOtherValues() {
        var flow = new AuthorizationSummaryFlow(new Store(7));
        var page = flow.enter(ACCOUNT, List.of(), State.INITIAL);
        assertThat(flow.enter(ACCOUNT, Arrays.asList("", " ", "s"), page.state()).transfer())
                .isEqualTo(new Transfer("COPAUS1C", "CPVD", ACCOUNT, key(2)));
        assertThat(flow.enter(ACCOUNT, List.of("S", "S"), page.state()).transfer())
                .isEqualTo(new Transfer("COPAUS1C", "CPVD", ACCOUNT, key(0)));
        var invalid = flow.enter(ACCOUNT, List.of("X", "S"), page.state());
        assertThat(invalid.transfer()).isNull();
        assertThat(invalid.message()).isEqualTo("Invalid selection. Valid value is S");
        assertThat(invalid.rows()).hasSize(5);
    }

    @Test
    void stateThatTheScreenCannotProduceIsRejected() {
        assertThatThrownBy(() -> AuthorizationSummaryFlow.validate(new State(ACCOUNT, 21, List.of(), null, false,
                List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthorizationSummaryFlow.validate(new State(ACCOUNT, 2, List.of(key(0)), null,
                false, List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthorizationSummaryFlow.validate(new State(ACCOUNT, 0, List.of(), "x", false,
                List.of()))).isInstanceOf(IllegalArgumentException.class);
    }
}
