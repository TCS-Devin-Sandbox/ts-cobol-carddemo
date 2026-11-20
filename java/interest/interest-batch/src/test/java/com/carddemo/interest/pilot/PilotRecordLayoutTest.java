package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PilotRecordLayoutTest {

    private static byte[] padded(String text, int lrecl) {
        byte[] out = new byte[lrecl];
        Arrays.fill(out, (byte) ' ');
        byte[] src = text.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    private static final String ACCOUNT_2001 =
            "00000002001Y00000010000{00000050000{00000025000{2019-03-152026-12-312026-12-3100000001234E00000006789}10115     GOLD      ";

    @Test
    void d_signedZonedOverpunchDecodesBothSigns() {
        byte[] r = padded("0000010000{0000000123}", 22);
        assertThat(PilotFieldCodec.signedZoned(r, 0, 11, 2, "T", 1)).isEqualTo(new BigDecimal("1000.00"));
        assertThat(PilotFieldCodec.signedZoned(r, 11, 11, 2, "T", 1)).isEqualTo(new BigDecimal("-12.30"));
    }

    @Test
    void d_signedZonedEncodesNegativeAndZero() {
        assertThat(new String(PilotFieldCodec.encodeSignedZoned(new BigDecimal("-3.00"), 11, 2), StandardCharsets.ISO_8859_1))
                .isEqualTo("0000000030}");
        assertThat(new String(PilotFieldCodec.encodeSignedZoned(new BigDecimal("0.00"), 11, 2), StandardCharsets.ISO_8859_1))
                .isEqualTo("0000000000{");
    }

    @Test
    void INT_R10_signByteOutsideProfileIsUnreadable() {
        byte[] r = padded("0000010000X", 11);
        assertThatThrownBy(() -> PilotFieldCodec.signedZoned(r, 0, 11, 2, "TCATBALF", 2))
                .isInstanceOf(PilotFailure.UnreadableInput.class)
                .hasMessageContaining("TCATBALF").hasMessageContaining("record=2");
    }

    @Test
    void d_unsignedFieldsKeepDigitWidth() {
        CategoryBalance b = CategoryBalance.decode(padded("000000020010100010000010000{", 50), 1);
        assertThat(b.acctId()).isEqualTo("00000002001");
        assertThat(b.categoryCode()).isEqualTo("0001");
        assertThat(b.balance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(b.legacyKey()).hasSize(17);
    }

    @Test
    void j_accountRewriteChangesOnlyA03A09A10AndKeepsFiller() {
        byte[] raw = padded(ACCOUNT_2001, 300);
        raw[299] = (byte) 0x7E;
        Account account = Account.decode(raw, 1);
        assertThat(account.cycleCredit()).isEqualTo(new BigDecimal("123.45"));
        assertThat(account.cycleDebit()).isEqualTo(new BigDecimal("-678.90"));
        byte[] out = account.rewrittenWith(AccountDelta.closing(account, new BigDecimal("1010.00")));
        assertThat(out).hasSize(300);
        for (int i = 0; i < 300; i++) {
            boolean changed = (i >= 12 && i < 24) || (i >= 78 && i < 102);
            if (!changed) {
                assertThat(out[i]).as("byte %d", i).isEqualTo(raw[i]);
            }
        }
        String text = new String(out, StandardCharsets.ISO_8859_1);
        assertThat(text.substring(12, 24)).isEqualTo("00000010100{");
        assertThat(text.substring(78, 102)).isEqualTo("00000000000{00000000000{");
    }

    @Test
    void INT_R05_g_transactionRecordLayoutAndConstants() {
        Account account = Account.decode(padded(ACCOUNT_2001, 300), 1);
        CardXref card = CardXref.decode(padded("400000000000200100000200100000002001", 50), 1);
        InterestTransaction tx = InterestTransaction.interestCharge(1, "2022071800000001", account, card,
                new BigDecimal("10.00"), "2022-07-05-10.00.00.000000");
        String record = new String(tx.toRecord(), StandardCharsets.ISO_8859_1);
        assertThat(record).hasSize(350);
        assertThat(record.substring(0, 32)).isEqualTo("2022071800000001010005System    ");
        assertThat(record.substring(32, 132).stripTrailing()).isEqualTo("Int. for a/c 00000002001");
        assertThat(record.substring(132, 143)).isEqualTo("0000000100{");
        assertThat(record.substring(143, 152)).isEqualTo("000000000");
        assertThat(record.substring(152, 262).isBlank()).isTrue();
        assertThat(record.substring(262, 330))
                .isEqualTo("40000000000020012022-07-05-10.00.00.0000002022-07-05-10.00.00.000000");
        assertThat(record.substring(330)).isEqualTo(" ".repeat(20));
    }
}
