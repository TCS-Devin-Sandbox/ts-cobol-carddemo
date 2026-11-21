package com.carddemo.batch.statements;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatementProgramTest {
    private static final Path FIXTURES = Path.of("../../fixtures/statements");

    @ParameterizedTest
    @ValueSource(strings = {"s01-mixed-cards", "s02-empty-transactions", "s03-empty-xrefs",
            "s04-missing-customer", "s05-missing-account"})
    void matchesGoldenReportsWithoutTheDatabase(String fixture) throws Exception {
        Path input = FIXTURES.resolve(fixture).resolve("input");
        Path expected = FIXTURES.resolve(fixture).resolve("expected");
        var trxfl = TransactionSort.sortAndReformat(lines(input.resolve("AWS.M2.CARDDEMO.DALYTRAN.PS.INIT.txt")));

        var result = StatementProgram.run(trxfl, new FixtureMasters(input));

        assertEquals(lines(expected.resolve("datasets/AWS.M2.CARDDEMO.TRXFL.SEQ.txt")),
                trxfl.stream().map(String::stripTrailing).toList());
        assertEquals(lines(expected.resolve("datasets/AWS.M2.CARDDEMO.STATEMNT.PS.txt")), result.text());
        assertEquals(lines(expected.resolve("datasets/AWS.M2.CARDDEMO.STATEMNT.HTML.txt")), result.html());
        assertEquals(Files.readString(expected.resolve("sysout/CREASTMT.STEP040.txt"), StandardCharsets.ISO_8859_1),
                result.sysout());
        assertEquals(fixture.matches("s0[245].*") ? 11 : 0, result.returnCode());
    }

    @Test
    void editsAmountsLikeTheCobolPictures() {
        assertEquals("      125.00 ", StatementFormat.zeroSuppressed(new BigDecimal("125.00")));
        assertEquals("      200.00-", StatementFormat.zeroSuppressed(new BigDecimal("-200.00")));
        assertEquals("         .05-", StatementFormat.zeroSuppressed(new BigDecimal("-0.05")));
        assertEquals("         .00 ", StatementFormat.zeroSuppressed(new BigDecimal("0.00")));
        assertEquals("000000500.00 ", StatementFormat.unsignedEdited(new BigDecimal("500.00")));
        assertEquals("234567890.12-", StatementFormat.unsignedEdited(new BigDecimal("-1234567890.12")));
    }

    private static List<String> lines(Path path) throws Exception {
        return Files.readAllLines(path, StandardCharsets.ISO_8859_1);
    }

    private static final class FixtureMasters implements StatementMasters {
        private final List<Map<String, Object>> xrefs;
        private final List<Map<String, Object>> customers;
        private final List<Map<String, Object>> accounts;

        FixtureMasters(Path input) throws Exception {
            xrefs = decode(input.resolve("AWS.M2.CARDDEMO.CARDXREF.PS.txt"), CardDemoLayout.CVACT03Y);
            customers = decode(input.resolve("AWS.M2.CARDDEMO.CUSTDATA.PS.txt"), CardDemoLayout.CVCUS01Y);
            accounts = decode(input.resolve("AWS.M2.CARDDEMO.ACCTDATA.PS.txt"), CardDemoLayout.CVACT01Y);
        }

        @Override
        public List<Map<String, Object>> crossReferences() {
            return xrefs.stream().sorted((a, b) -> ((String) a.get("XREF-CARD-NUM"))
                    .compareTo((String) b.get("XREF-CARD-NUM"))).toList();
        }

        @Override
        public Optional<Map<String, Object>> customer(BigDecimal id) {
            return customers.stream().filter(c -> ((BigDecimal) c.get("CUST-ID")).compareTo(id) == 0).findFirst();
        }

        @Override
        public Optional<Map<String, Object>> account(BigDecimal id) {
            return accounts.stream().filter(a -> ((BigDecimal) a.get("ACCT-ID")).compareTo(id) == 0).findFirst();
        }

        private static List<Map<String, Object>> decode(Path file, CardDemoLayout layout) throws Exception {
            var codec = new FixedRecordCodec(layout.load());
            int length = layout.load().length();
            return lines(file).stream().map(line -> codec.decode(
                    StatementProgram.pad(line, length).getBytes(StandardCharsets.ISO_8859_1))).toList();
        }
    }
}
