package com.carddemo.vsammq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.vsammq.AccountInquiryReply.Account;
import com.carddemo.vsammq.AccountInquiryReply.Request;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** COACCT01 4000-PROCESS-REQUEST-REPLY reply bytes, no database. */
class AccountInquiryReplyTest {

    @ParameterizedTest
    @CsvSource({
            "0.00, 00000000000{", "1.23, 00000000012C", "500.00, 00000005000{", "9999999999.99, 99999999999I",
            "-0.01, 00000000000J", "-1234.56, 00000012345O", "-10.00, 00000000100}", "-9999999999.99, 99999999999R",
            "7, 00000000070{"})
    void signedDisplayOverpunchesTheLastDigit(String value, String expected) {
        assertThat(AccountInquiryReply.signed(new BigDecimal(value))).isEqualTo(expected);
    }

    @Test
    void signedDisplayRejectsValuesOutsideThePicture() {
        assertThatThrownBy(() -> AccountInquiryReply.signed(new BigDecimal("10000000000.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountInquiryReply.signed(new BigDecimal("0.001")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void foundReplyIsTheLabelledResponseOf252BytesPaddedTo1000() {
        var account = new Account(42, "N", new BigDecimal("-1234.56"), new BigDecimal("9000.00"),
                new BigDecimal("300.00"), "2020-01-01", "2030-01-01", "2025-03-01",
                new BigDecimal("25.01"), new BigDecimal("-6.50"), "GROUP     ");
        String reply = AccountInquiryReply.found(account);

        String response = "ACCOUNT ID : 00000000042" + "ACCOUNT STATUS : N" + "BALANCE : 00000012345O"
                + "CREDIT LIMIT : 00000090000{" + "CASH LIMIT : 00000003000{" + "OPEN DATE : 2020-01-01"
                + "EXPR DATE : 2030-01-01" + "REIS DATE : 2025-03-01" + "CREDIT BAL : 00000000250A"
                + "DEBIT BAL : 00000000065}" + "GROUP ID : GROUP     ";
        assertThat(response).hasSize(252);
        assertThat(reply).hasSize(1000).startsWith(response);
        assertThat(reply.substring(252)).isBlank();
    }

    @Test
    void foundReplyPadsShortTextFieldsToTheirPictures() {
        var account = new Account(1, "Y", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "", "", "",
                BigDecimal.ZERO, BigDecimal.ZERO, "G");
        assertThat(AccountInquiryReply.found(account).substring(0, 252)).endsWith("GROUP ID : G         ")
                .contains("OPEN DATE :           EXPR DATE : ");
    }

    @Test
    void requestTakesFunctionAndKeyFromTheFixedLayoutAndPadsShortMessages() {
        var request = Request.parse("INQA00000001001" + "x".repeat(985));
        assertThat(request).isEqualTo(new Request("INQA", "00000001001"));
        assertThat(request.isInquiry()).isTrue();
        assertThat(request.accountId()).isEqualTo(1001L);
        assertThat(Request.parse("INQA")).isEqualTo(new Request("INQA", "           "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INQA00000000000", "inqa00000001001", "ACCT00000001001", "INQ 00000001001",
            "INQA0000000100 ", "INQA", ""})
    void onlyInqaWithANonZeroNumericKeyIsAnInquiry(String message) {
        assertThat(Request.parse(message).isInquiry()).isFalse();
    }

    @Test
    void notFoundReplyNamesTheKey() {
        String reply = AccountInquiryReply.notFound(new Request("INQA", "00000009999"));
        assertThat(reply).hasSize(1000).startsWith("INVALID REQUEST PARAMETERS ACCT ID : 00000009999 ");
        assertThat(reply.substring(48)).isBlank();
    }

    @Test
    void badFunctionReplyEchoesKeyAndFunctionBytes() {
        String reply = AccountInquiryReply.invalid(Request.parse("DATE00000001001"));
        String text = "INVALID REQUEST PARAMETERS ACCT ID : 00000001001FUNCTION : DATE";
        assertThat(reply).hasSize(1000).startsWith(text);
        assertThat(reply.substring(text.length())).isBlank();
        assertThat(AccountInquiryReply.invalid(Request.parse("")))
                .startsWith("INVALID REQUEST PARAMETERS ACCT ID :            FUNCTION :     ").hasSize(1000);
    }
}
