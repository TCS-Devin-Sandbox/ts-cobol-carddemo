package com.carddemo.batch.statements;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.RecordLayout;

/**
 * CBSTM03A (with CBSTM03B as its file driver): one text and one HTML statement per cross-reference,
 * listing that card's transactions from the sorted TRXFL KSDS and their signed total.
 */
final class StatementProgram {
    static final int CARD_TABLE_SIZE = 51;
    static final int TRANSACTIONS_PER_CARD = 10;
    private static final BigDecimal TOTAL_MODULUS = new BigDecimal("1000000000");
    private static final FixedRecordCodec AMOUNT = new FixedRecordCodec(new RecordLayout(List.of(
            new RecordLayout.Field("TRNX-AMT", RecordLayout.Kind.DISPLAY, 11, 11, 2, true))));

    record Result(List<String> text, List<String> html, String sysout, int returnCode) { }

    private record Transaction(String id, String description, BigDecimal amount) { }

    private record Card(String number, List<Transaction> transactions) { }

    /** Raised by 9999-ABEND-PROGRAM; off-host the CEE3ABD call ends the step with RC 11 (docs/BASELINE.md). */
    private static final class Abend extends Exception {
        Abend(String message) {
            super(message);
        }
    }

    private final List<String> text = new ArrayList<>();
    private final List<String> html = new ArrayList<>();
    private final Deque<String> openFiles = new ArrayDeque<>();
    private final StringBuilder sysout = new StringBuilder();

    static Result run(List<String> trxflRecords, StatementMasters masters) throws Exception {
        return new StatementProgram().execute(trxflRecords, masters);
    }

    private Result execute(List<String> trxflRecords, StatementMasters masters) throws Exception {
        open("STMT-FILE", "STMTFILE");
        open("HTML-FILE", "HTMLFILE");
        try {
            open("TRNX-FILE", "TRNXFILE");
            if (trxflRecords.isEmpty()) fileError("TRNXFILE", "10");
            List<Card> cards = loadTransactions(trxflRecords);
            open("XREF-FILE", "XREFFILE");
            open("CUST-FILE", "CUSTFILE");
            open("ACCT-FILE", "ACCTFILE");
            for (var xref : masters.crossReferences()) {
                var customer = keyed("CUSTFILE", masters.customer((BigDecimal) xref.get("XREF-CUST-ID")));
                var account = keyed("ACCTFILE", masters.account((BigDecimal) xref.get("XREF-ACCT-ID")));
                writeStatement((String) xref.get("XREF-CARD-NUM"), customer, account, cards);
            }
            return new Result(text, html, sysout.toString(), 0);
        } catch (Abend abend) {
            sysout.append(abend.getMessage()).append("ABENDING PROGRAM\nCEE3ABD: USER ABEND U\n\n")
                    .append("attempt to reference unallocated memory (signal SIGSEGV)\n\n");
            while (!openFiles.isEmpty()) sysout.append(openFiles.pop()).append('\n');
            sysout.append("\n Last statement of CEE3ABD unknown\n Last statement of CBSTM03A unknown\n");
            return new Result(text, html, sysout.toString(), 11);
        }
    }

    private void open(String file, String dd) {
        openFiles.push("libcob: warning: implicit CLOSE of " + file + " ('" + dd + "')");
    }

    private static void fileError(String dd, String status) throws Abend {
        throw new Abend("ERROR READING " + dd + "\nRETURN CODE: " + status + "\n");
    }

    private static Map<String, Object> keyed(String dd, Optional<Map<String, Object>> record) throws Abend {
        if (record.isEmpty()) fileError(dd, "23");
        return record.get();
    }

    /** 8500-READTRNX-READ: consecutive records with the same card fill one WS-CARD-TBL entry. */
    private static List<Card> loadTransactions(List<String> records) {
        var cards = new ArrayList<Card>();
        for (String record : records) {
            String line = pad(record, TransactionSort.LRECL);
            String card = line.substring(0, 16);
            if (cards.isEmpty() || !cards.get(cards.size() - 1).number().equals(card)) {
                if (cards.size() == CARD_TABLE_SIZE) {
                    throw new IllegalStateException("More than " + CARD_TABLE_SIZE + " cards overflow WS-CARD-TBL");
                }
                cards.add(new Card(card, new ArrayList<>()));
            }
            var transactions = cards.get(cards.size() - 1).transactions();
            if (transactions.size() == TRANSACTIONS_PER_CARD) {
                throw new IllegalStateException("More than " + TRANSACTIONS_PER_CARD + " transactions on card "
                        + card + " overflow WS-TRAN-TBL");
            }
            var amount = (BigDecimal) AMOUNT.decode(line.substring(148, 159).getBytes(StandardCharsets.ISO_8859_1))
                    .get("TRNX-AMT");
            transactions.add(new Transaction(line.substring(16, 32), line.substring(48, 148), amount));
        }
        return cards;
    }

    private void writeStatement(String cardNumber, Map<String, Object> customer, Map<String, Object> account,
            List<Card> cards) {
        String name = StatementFormat.concat(75, StatementFormat.untilSpace(text(customer, "CUST-FIRST-NAME")), " ",
                StatementFormat.untilSpace(text(customer, "CUST-MIDDLE-NAME")), " ",
                StatementFormat.untilSpace(text(customer, "CUST-LAST-NAME")), " ");
        String address1 = pad(text(customer, "CUST-ADDR-LINE-1"), 50);
        String address2 = pad(text(customer, "CUST-ADDR-LINE-2"), 50);
        String address3 = StatementFormat.concat(80, StatementFormat.untilSpace(text(customer, "CUST-ADDR-LINE-3")),
                " ", StatementFormat.untilSpace(text(customer, "CUST-ADDR-STATE-CD")), " ",
                StatementFormat.untilSpace(text(customer, "CUST-ADDR-COUNTRY-CD")), " ",
                StatementFormat.untilSpace(text(customer, "CUST-ADDR-ZIP")), " ");
        String accountId = pad(StatementFormat.digits((BigDecimal) account.get("ACCT-ID"), 11), 20);
        String balance = StatementFormat.unsignedEdited((BigDecimal) account.get("ACCT-CURR-BAL"));
        String fico = pad(StatementFormat.digits((BigDecimal) customer.get("CUST-FICO-CREDIT-SCORE"), 3), 20);

        StatementFormat.header(text, html, name, address1, address2, address3, accountId, balance, fico);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Card card : cards) {
            if (card.number().compareTo(cardNumber) > 0) break;
            if (!card.number().equals(cardNumber)) continue;
            for (Transaction transaction : card.transactions()) {
                total = total.add(transaction.amount()).remainder(TOTAL_MODULUS);
                StatementFormat.transaction(text, html, transaction.id(), transaction.description().substring(0, 49),
                        StatementFormat.zeroSuppressed(transaction.amount()));
            }
        }
        StatementFormat.trailer(text, html, StatementFormat.zeroSuppressed(total));
    }

    private static String text(Map<String, Object> record, String field) {
        return (String) record.get(field);
    }

    static String pad(String value, int length) {
        String text = value == null ? "" : value;
        return text.length() >= length ? text.substring(0, length) : text + " ".repeat(length - text.length());
    }
}
