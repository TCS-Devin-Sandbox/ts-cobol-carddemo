package com.carddemo.batch.common.codec;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthRecordCodecTest {
    @Test
    void summaryRepeatsFiveTwoByteStatusesBeforeThePackedAmounts() throws Exception {
        var layout = CardDemoLayout.CIPAUSMY.load();
        var codec = CardDemoLayout.CIPAUSMY.ebcdicCodec();
        var fields = blankRecord(layout);
        for (int i = 1; i <= 5; i++) fields.put("PA-ACCOUNT-STATUS[" + i + "]", "A" + i);
        fields.put("PA-CREDIT-LIMIT", new BigDecimal("999999999.99"));
        fields.put("PA-DECLINED-AUTH-CNT", new BigDecimal("-9999"));

        byte[] bytes = codec.encode(fields);
        assertHexAt(bytes, 16, "c1f1c1f2c1f3c1f4c1f5");
        assertHexAt(bytes, 26, "99999999999c");
        assertHexAt(bytes, 52, "d8f1");
        assertEquals("A5", codec.decode(bytes).get("PA-ACCOUNT-STATUS[5]"));
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)));
    }

    @Test
    void authfrdsUsesTheCobolHostLayoutNotTheSqlColumnWidths() throws Exception {
        var layout = CardDemoLayout.AUTHFRDS.load();
        var codec = CardDemoLayout.AUTHFRDS.ebcdicCodec();
        var fields = blankRecord(layout);
        fields.put("AUTH-TS", "2022-07-05-10.00.00.123456");
        fields.put("TRANSACTION-AMT", new BigDecimal("-9999999999.99"));
        fields.put("POS-ENTRY-MODE", new BigDecimal("321"));
        fields.put("MERCHANT-NAME-LEN", new BigDecimal("3"));
        fields.put("MERCHANT-NAME-TEXT", "ABC");
        fields.put("ACCT-ID", new BigDecimal("12345678901"));
        fields.put("CUST-ID", new BigDecimal("234567890"));

        byte[] bytes = codec.encode(fields);
        assertEquals(204, bytes.length);
        assertHexAt(bytes, 80, "0999999999999d");
        assertHexAt(bytes, 101, "0141");
        assertHexAt(bytes, 118, "0003c1c2c3");
        assertHexAt(bytes, 193, "12345678901c234567890c");
        var decoded = codec.decode(bytes);
        assertEquals(fields.get("AUTH-TS"), decoded.get("AUTH-TS"));
        assertEquals(fields.get("TRANSACTION-AMT"), decoded.get("TRANSACTION-AMT"));
        assertEquals("ABC" + " ".repeat(19), decoded.get("MERCHANT-NAME-TEXT"));
        assertArrayEquals(bytes, codec.encode(decoded));
    }

    private static LinkedHashMap<String, Object> blankRecord(RecordLayout layout) {
        var map = new LinkedHashMap<String, Object>();
        for (var field : layout.fields()) {
            map.put(field.name(), field.kind() == RecordLayout.Kind.TEXT ? "" : BigDecimal.ZERO);
        }
        return map;
    }

    private static void assertHexAt(byte[] bytes, int offset, String hex) {
        byte[] expected = HexFormat.of().parseHex(hex);
        assertArrayEquals(expected, Arrays.copyOfRange(bytes, offset, offset + expected.length));
    }
}
