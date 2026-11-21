package com.carddemo.batch.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.RecordLayout.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FeeDriverTest {
    @ParameterizedTest
    @CsvSource({"1250.00,15.44,1234.56,10", "10.00,10.00,0.00,0", "10.00,30.01,-20.01,0"})
    void mapsAccountToRequest(String credit, String debit, String balance, int days) throws Exception {
        var request = new FeeDriver().request(account(credit, debit));
        assertEquals("00000000042", request.FEE_ACCT_ID());
        assertEquals("STANDARD  ", request.FEE_GROUP_ID());
        assertEquals(new BigDecimal(balance), request.FEE_CYCLE_BAL());
        assertEquals(days, request.FEE_LATE_DAYS());
        assertEquals(30, request.FEE_CYCLE_DAYS());
        assertEquals(new BigDecimal("0.00"), request.FEE_AMT());
        assertEquals(new BigDecimal("0.00"), request.FEE_AVG_DAILY());
        assertEquals(0, request.FEE_RC());
    }

    @Test
    void writesEveryAccountInOrderAsEightyByteLines() throws Exception {
        var input = new ByteArrayOutputStream();
        input.write(account("1250.00", "15.44"));
        input.write(account("10.00", "30.01"));
        var output = new ByteArrayOutputStream();
        new FeeDriver().run(new ByteArrayInputStream(input.toByteArray()), output);
        String positive = "00000000042 STANDARD          1234.56       30.86 00" + " ".repeat(28);
        String negative = "00000000042 STANDARD           -20.01        0.00 00" + " ".repeat(28);
        assertEquals(160, output.size());
        assertEquals(positive + negative, output.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    void emptyAndTruncatedInput() throws Exception {
        var driver = new FeeDriver();
        var output = new ByteArrayOutputStream();
        driver.run(new ByteArrayInputStream(new byte[0]), output);
        assertEquals(0, output.size());
        assertThrows(IOException.class, () -> driver.run(new ByteArrayInputStream(new byte[299]), output));
        assertThrows(IllegalArgumentException.class, () -> driver.request(new byte[299]));
    }

    private static byte[] account(String credit, String debit) throws IOException {
        var layout = CardDemoLayout.CVACT01Y.load();
        var values = new HashMap<String, Object>();
        for (var field : layout.fields()) {
            values.put(field.name(), field.kind() == Kind.TEXT ? "" : BigDecimal.ZERO.setScale(field.scale()));
        }
        values.put("ACCT-ID", new BigDecimal("42"));
        values.put("ACCT-GROUP-ID", "STANDARD");
        values.put("ACCT-CURR-CYC-CREDIT", new BigDecimal(credit));
        values.put("ACCT-CURR-CYC-DEBIT", new BigDecimal(debit));
        return new FixedRecordCodec(layout).encode(values);
    }
}
