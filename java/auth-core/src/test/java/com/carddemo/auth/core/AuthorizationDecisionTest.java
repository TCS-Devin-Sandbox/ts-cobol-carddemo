package com.carddemo.auth.core;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static com.carddemo.auth.core.AuthorizationService.decide;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationDecisionTest {
    @Test
    void paragraph2100ParsesTheFixedFieldsAndThirteenCharacterNumvalInput() {
        var request = request("+1234567890.12");
        assertEquals(new BigDecimal("1234567890.10"), request.amount());
        assertEquals("1234567890123456", request.card());
        assertEquals("1234  ", request.time());
        assertEquals("T1             ", request.transactionId());
        assertEquals(123, request.processingCode());
        assertEquals(1, request.entryMode());
    }

    @Test
    void paragraph6000KeepsFirstReplyWidthWithoutAccumulatingAndFormatsNegativeAmounts() {
        var request = request("1.2-");
        byte[] expected = "1234567890123456,T1             ,1234  ,00,0000,         -1.20, ".getBytes(ISO_8859_1);
        assertArrayEquals(expected, request.reply("00", "0000", request.amount()));
        assertArrayEquals(expected, request.reply("00", "0000", request.amount()));
    }

    @Test
    void paragraph6000UsesSummaryBeforeMasterAndMissingFlagsOnlyAffectDeclines() {
        var amount = new BigDecimal("1.20");
        var master = new AuthorizationStore.Master(1, 2, new BigDecimal("3.20"), AuthRequest.ZERO,
                new BigDecimal("2.00"), true);
        var missingCustomer = new AuthorizationStore.Master(1, 2, master.creditLimit(), master.cashLimit(),
                master.currentBalance(), false);
        assertEquals("00", decide(amount, null, master).code());
        assertEquals("4100", decide(amount, new BigDecimal("1.19"), master).reason());
        assertEquals("3100", decide(amount, null, null).reason());
        assertEquals("00", decide(amount, null, missingCustomer).code());
        assertEquals("3100", decide(amount, AuthRequest.ZERO, missingCustomer).reason());
    }

    private static AuthRequest request(String amount) {
        return AuthRequest.parse("220705,1234,12345678901234567,AUTH,0727,MSG,TEST,000123," + amount
                + ",5411,USA,01,M1,SHOP,CITY,NY,12345,T1");
    }
}
