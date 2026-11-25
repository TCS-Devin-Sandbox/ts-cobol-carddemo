package com.carddemo.auth.core;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.carddemo.batch.common.ims.Dfsurgu0Unload;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class AuthorizationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    private static final Path UNLOAD = Path.of("../../app/app-authorization-ims-db2-mq/data/EBCDIC",
            "AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat");
    private static final Instant NOW = Instant.parse("2022-07-05T10:00:00.123Z");
    private static final String CARD = "9680294154603697";
    private static final String UNKNOWN = "9999999999999999";
    private final DriverManagerDataSource source = new DriverManagerDataSource();
    private JdbcTemplate jdbc;
    private List<String> requests;

    @BeforeEach
    void seedMasterAndBuildRequestsFromUnloadDetails() throws Exception {
        source.setUrl(PG.getJdbcUrl());
        source.setUsername(PG.getUsername());
        source.setPassword(PG.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        jdbc.update("TRUNCATE pending_auth_detail, pending_auth_summary, card_xref, account, customer");
        jdbc.update("INSERT INTO card_xref VALUES (?, 1, 1, ' ')", CARD);
        jdbc.update("INSERT INTO account VALUES (1, 'Y', 0.76, 2.00, 0.50, '', '', '', 0, 0, '', '', '')");
        jdbc.update("INSERT INTO customer VALUES (1, '', '', '', '', '', '', '', '', '', '', '', 0, '', '', '', '', 0, '')");
        requests = new ArrayList<>();
        var decoder = new Dfsurgu0Unload();
        try (var input = Files.newInputStream(UNLOAD)) {
            Dfsurgu0Unload.Frame frame;
            while (requests.size() < 3 && (frame = decoder.readRecord(input)) != null) {
                if (frame instanceof Dfsurgu0Unload.Segment segment
                        && segment.type() == Dfsurgu0Unload.SegmentType.PAUTDTL1) {
                    requests.add(csv(segment.fields()));
                }
            }
        }
        assertEquals(3, requests.size());
    }

    @Test
    void paragraphs5500To8500PreserveInvocationStateAndWriteReplyBeforeAnyChanges() {
        AtomicLong millis = new AtomicLong();
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
            @Override public Instant instant() { return NOW.plusMillis(millis.getAndIncrement()); }
        };
        var service = new AuthorizationService(source, clock);
        var invocation = service.newInvocation();
        invocation.process(requests.getFirst(), bytes -> {
            assertArrayEquals("9680294154603697,960b9b5480d045a,041252,00,0000,          1.24, ".getBytes(ISO_8859_1), bytes);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
            assertEquals(0, count("pending_auth_summary"));
            assertEquals(0, count("pending_auth_detail"));
        });
        assertEquals(new BigDecimal("1.24"), summaryAmount("credit_balance"));
        var unknown = requests.getFirst().replace(CARD, UNKNOWN);
        invocation.process(unknown, bytes -> assertEquals(
                "9999999999999999,960b9b5480d045a,041252,05,3100,          0.00, ", new String(bytes, ISO_8859_1)));
        jdbc.update("UPDATE account SET credit_limit = 20.00 WHERE account_id = 1");
        invocation.process(requests.getLast(), bytes -> {
            assertArrayEquals("9680294154603697,c55b7da992ce4ee,030910,05,4100,          0.00, ".getBytes(ISO_8859_1), bytes);
            assertEquals(1, count("pending_auth_detail"));
            assertEquals(new BigDecimal("2.00"), summaryAmount("credit_limit"));
        });
        assertEquals(new BigDecimal("20.00"), summaryAmount("credit_limit"));
        assertEquals(new BigDecimal("1.24"), summaryAmount("declined_auth_amount"));
        assertEquals(List.of("D", "P"), jdbc.queryForList(
                "SELECT match_status FROM pending_auth_detail ORDER BY auth_date_inv, auth_time_inv", String.class));
        assertEquals(2, count("pending_auth_detail"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM pending_auth_detail WHERE auth_date_inv = 77813 AND auth_time_inv = 899999876
                  AND auth_original_date = '231027' AND auth_original_time = '041252'
                  AND auth_id_code = '041252' AND transaction_amount = 1.24 AND approved_amount = 1.24
                  AND fraud_status = ' ' AND fraud_report_date = ' '
                """, Integer.class));
        invocation.process(unknown, bytes -> assertEquals(
                "9999999999999999,960b9b5480d045a,041252,00,0000,          1.24, ", new String(bytes, ISO_8859_1)));
        service.newInvocation().process(unknown, bytes -> assertEquals(
                "9999999999999999,960b9b5480d045a,041252,05,3100,          0.00, ", new String(bytes, ISO_8859_1)));
        assertEquals(2, count("pending_auth_detail"));
    }

    @Test
    void paragraphs7100And8000DoNotWriteOnSendFailureAndRollBackWritesOnDetailFailure() {
        var service = new AuthorizationService(source, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(IllegalStateException.class, () -> service.newInvocation().process(requests.getFirst(), bytes -> {
            throw new IllegalStateException("send failed");
        }));
        assertEquals(0, count("pending_auth_summary"));
        assertEquals(0, count("pending_auth_detail"));

        var invocation = service.newInvocation();
        var replies = new ArrayList<byte[]>();
        invocation.process(requests.getFirst(), replies::add);
        assertThrows(DuplicateKeyException.class, () -> invocation.process(requests.getLast(), replies::add));
        assertEquals(2, replies.size());
        assertArrayEquals("9680294154603697,c55b7da992ce4ee,030910,05,4100,          0.00, ".getBytes(ISO_8859_1), replies.getLast());
        assertEquals(1, count("pending_auth_detail"));
        assertEquals(new BigDecimal("0.00"), summaryAmount("declined_auth_amount"));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private BigDecimal summaryAmount(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM pending_auth_summary", BigDecimal.class);
    }

    private static String csv(Map<String, Object> fields) {
        return List.of("AUTH-ORIG-DATE", "AUTH-ORIG-TIME", "CARD-NUM", "AUTH-TYPE", "CARD-EXPIRY-DATE",
                        "MESSAGE-TYPE", "MESSAGE-SOURCE", "PROCESSING-CODE", "TRANSACTION-AMT",
                        "MERCHANT-CATAGORY-CODE", "ACQR-COUNTRY-CODE", "POS-ENTRY-MODE", "MERCHANT-ID",
                        "MERCHANT-NAME", "MERCHANT-CITY", "MERCHANT-STATE", "MERCHANT-ZIP", "TRANSACTION-ID")
                .stream().map(key -> fields.get("PA-" + key).toString()).collect(Collectors.joining(","));
    }
}
