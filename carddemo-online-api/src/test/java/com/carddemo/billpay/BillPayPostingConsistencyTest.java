package com.carddemo.billpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * E10 against C2: the account CB00 leaves behind is fed, through the CVACT01Y/CVTRA06Y record layouts, to the real
 * C2 posting jar (CBTRN02C port) and compared with what C2 computes for the same payment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
class BillPayPostingConsistencyTest {
    private static final Path JAR = Path.of(System.getProperty("carddemo.posting-jar",
            "../java/posting/target/carddemo-posting.jar"));
    private static final String CARD = "4000000000000421";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @TempDir Path work;

    @BeforeEach void seed() {
        assumeTrue(Files.isRegularFile(JAR), "C2 posting jar not built: mvn -f java/pom.xml -pl posting -am package");
        jdbc.update("DELETE FROM \"transaction\"");
        jdbc.update("DELETE FROM card_xref WHERE account_id = 42");
        jdbc.update("DELETE FROM account WHERE account_id = 42");
        jdbc.update("INSERT INTO account VALUES (42, 'Y', 250.00, 1000.00, 200.00, '2020-01-01', '2030-12-31', "
                + "'2025-03-01', 300.00, -50.00, '00000', 'GROUP', '')");
        jdbc.update("INSERT INTO card_xref VALUES (?, 7, 42, '')", CARD);
    }

    @Test void C2_posts_from_the_balance_CB00_leaves_and_reaches_the_same_balance_for_the_same_payment() throws Exception {
        String before = acctdata();
        mvc.perform(post("/api/bill-payments").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"00000000042\",\"confirm\":\"Y\"}"))
                .andExpect(jsonPath("$.severity").value("success"));
        Map<String, Object> payment = jdbc.queryForMap("SELECT * FROM \"transaction\"");
        String after = acctdata();
        assertThat(amount(after, 12)).isEqualByComparingTo("0.00");

        // 1. C2 reads CB00's account record and posts the next day's purchase on top of it.
        String next = dalytran("0000000000000900", "01", 1, new BigDecimal("40.00"), "PURCHASE", "2022-07-06-09.00.00.000000");
        String posted = runPosting(after, next);
        assertThat(amount(posted, 12)).isEqualByComparingTo("40.00");
        assertThat(amount(posted, 78)).isEqualByComparingTo("340.00");
        assertThat(amount(posted, 90)).isEqualByComparingTo("-50.00");

        // 2. The same payment posted by C2 as a credit (its sign convention) lands on the same balance as CB00.
        BigDecimal paid = (BigDecimal) payment.get("amount");
        String credit = dalytran((String) payment.get("transaction_id"), "02", 2, paid.negate(),
                ((String) payment.get("description")).trim(), ((String) payment.get("origination_timestamp")).replace(' ', '-'));
        String c2 = runPosting(before, credit);
        assertThat(amount(c2, 12)).isEqualByComparingTo(amount(after, 12));
        // CB00 leaves the cycle totals alone; C2 books the credit to the cycle debit (documented in docs/online/CB00.md).
        assertThat(amount(after, 90)).isEqualByComparingTo("-50.00");
        assertThat(amount(c2, 90)).isEqualByComparingTo("-300.00");
        assertThat(amount(c2, 78)).isEqualByComparingTo(amount(after, 78));

        // 3. CB00 writes TRAN-AMT unsigned: replaying its TRANSACT row through C2 verbatim would add, not subtract.
        String verbatim = dalytran((String) payment.get("transaction_id"), "02", 2, paid, "BILL PAYMENT - ONLINE",
                ((String) payment.get("origination_timestamp")).replace(' ', '-'));
        assertThat(amount(runPosting(before, verbatim), 12)).isEqualByComparingTo("500.00");
    }

    private String acctdata() {
        Map<String, Object> a = jdbc.queryForMap("SELECT * FROM account WHERE account_id = 42");
        return String.format(Locale.ROOT, "%011d", ((BigDecimal) a.get("account_id")).longValueExact())
                + a.get("active_status") + zoned(a.get("current_balance"), 12) + zoned(a.get("credit_limit"), 12)
                + zoned(a.get("cash_credit_limit"), 12) + a.get("open_date") + a.get("expiration_date")
                + a.get("reissue_date") + zoned(a.get("current_cycle_credit"), 12)
                + zoned(a.get("current_cycle_debit"), 12) + pad(a.get("zip_code"), 10) + pad(a.get("account_group_id"), 10);
    }

    private static String dalytran(String id, String type, int category, BigDecimal amount, String description, String ts) {
        return id + type + String.format(Locale.ROOT, "%04d", category) + pad("POS TERM", 10) + pad(description, 100)
                + zoned(amount, 11) + "999999999" + pad("BILL PAYMENT", 50) + pad("N/A", 50) + pad("N/A", 10)
                + CARD + ts + pad("", 26);
    }

    /** Runs `carddemo-posting.jar --chain posting` and returns the ACCTDATA record it exports for account 42. */
    private String runPosting(String account, String daily) throws Exception {
        Path in = Files.createTempDirectory(work, "in");
        Path out = Files.createTempDirectory(work, "out");
        Files.writeString(in.resolve("AWS.M2.CARDDEMO.ACCTDATA.PS.txt"), account + "\n", ISO_8859_1);
        Files.writeString(in.resolve("AWS.M2.CARDDEMO.CARDXREF.PS.txt"), CARD + "00000000700000000042\n", ISO_8859_1);
        Files.writeString(in.resolve("AWS.M2.CARDDEMO.TCATBALF.PS.txt"), "", ISO_8859_1);
        Files.writeString(in.resolve("AWS.M2.CARDDEMO.DALYTRAN.PS.txt"), daily.stripTrailing() + "\n", ISO_8859_1);
        var launch = new ProcessBuilder("java", "-jar", JAR.toAbsolutePath().toString(), "--chain", "posting",
                "--input", in.toString(), "--out", out.toString(), "--clock", "2022-07-06 10:00:00.00")
                .redirectErrorStream(true).redirectOutput(work.resolve("posting.log").toFile());
        launch.environment().put("POSTING_JDBC_URL", postgres.getJdbcUrl());
        launch.environment().put("PGUSER", postgres.getUsername());
        launch.environment().put("PGPASSWORD", postgres.getPassword());
        Process process = launch.start();
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(Files.readString(work.resolve("posting.log"))).isZero();
        List<String> records = Files.readAllLines(out.resolve("datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt"), ISO_8859_1);
        assertThat(Files.readString(out.resolve("datasets/AWS.M2.CARDDEMO.DALYREJS.G0001V00.txt"))).isEmpty();
        return records.get(0);
    }

    private static String pad(Object value, int width) {
        String text = value == null ? "" : value.toString();
        return text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
    }

    /** Zoned decimal with an EBCDIC-style overpunched sign, as the fixture datasets store PIC S9(n)V99. */
    private static String zoned(Object value, int digits) {
        BigDecimal amount = (BigDecimal) value;
        String text = String.format(Locale.ROOT, "%0" + digits + "d", amount.movePointRight(2).abs().longValueExact());
        int last = text.charAt(digits - 1) - '0';
        char sign = amount.signum() < 0 ? "}JKLMNOPQR".charAt(last) : "{ABCDEFGHI".charAt(last);
        return text.substring(0, digits - 1) + sign;
    }

    private static BigDecimal amount(String record, int offset) {
        String field = record.substring(offset, offset + 12);
        char sign = field.charAt(11);
        int positive = "{ABCDEFGHI".indexOf(sign);
        int digit = positive >= 0 ? positive : "}JKLMNOPQR".indexOf(sign);
        BigDecimal value = new BigDecimal(field.substring(0, 11) + digit).movePointLeft(2);
        return positive >= 0 ? value : value.negate();
    }
}
