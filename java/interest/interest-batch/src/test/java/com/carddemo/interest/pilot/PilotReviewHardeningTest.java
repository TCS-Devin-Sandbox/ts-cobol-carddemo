package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Byte-level guards on the pilot's fixed-record boundary. */
class PilotReviewHardeningTest {

    private static final Path I01_ACCOUNTS =
            Path.of("../../../fixtures/interest/i01-baseline/input/AWS.M2.CARDDEMO.ACCTDATA.PS.txt");

    @Test
    void d_accountIdIsPic9AndNonDigitKeyIsUnreadable() {
        byte[] record = FixtureDatasets.readFixed(I01_ACCOUNTS, "ACCTFILE", 300).get(0);
        record[5] = 'X';
        assertThatThrownBy(() -> Account.decode(record, 1)).isInstanceOf(PilotFailure.UnreadableInput.class);
    }

    @Test
    void j_exportDropsOnlyTrailingSpacesSoOtherFillerBytesSurvive(@TempDir Path dir) throws Exception {
        byte[] record = " ".repeat(300).getBytes(StandardCharsets.ISO_8859_1);
        record[0] = '1';
        record[299] = '\t';
        Path file = dir.resolve("out.txt");
        FixtureDatasets.writeFixed(file, List.of(record));
        assertThat(FixtureDatasets.readFixed(file, "OUT", 300).get(0)).isEqualTo(record);
    }

    @Test
    void INT_R08_INT_Q05_idsAndTimestampsUseAsciiDigitsUnderAnyLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            assertThat(new TransactionIdSource("2022071800").idFor(1)).isEqualTo("2022071800000001");
            Clock clock = Clock.fixed(LocalDateTime.parse("2022-07-05T10:00:00").toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
            assertThat(new LegacyTimestamp(clock).sample()).isEqualTo("2022-07-05-10.00.00.000000");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void INT_H02_parmThatIsNotTenLabProfileBytesIsHeld() {
        assertThatThrownBy(() -> new TransactionIdSource("漢字漢字漢字漢字漢字"))
                .isInstanceOf(PilotFailure.UnprovenHostOutcome.class);
        assertThatThrownBy(() -> new TransactionIdSource("20220718\n0"))
                .isInstanceOf(PilotFailure.UnprovenHostOutcome.class);
    }
}
