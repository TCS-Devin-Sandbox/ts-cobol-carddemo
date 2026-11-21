package com.carddemo.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardSourceReachabilityTest {
    private String source(String program) throws Exception {
        return Files.readAllLines(Path.of("../app/cbl/" + program + ".cbl")).stream()
                .filter(line -> line.length() > 6 && line.charAt(6) != '*')
                .collect(Collectors.joining("\n"));
    }

    @Test @DisplayName("CCDL 9150-GETCARD-BYACCT: defined, never performed by 9000-READ-DATA")
    void alternateIndexLookupIsNotReachable() throws Exception {
        String source = source("COCRDSLC");
        assertThat(source).contains("9150-GETCARD-BYACCT.", "PERFORM 9100-GETCARD-BYACCTCARD")
                .doesNotContain("PERFORM 9150-GETCARD-BYACCT", "GO TO 9150-GETCARD-BYACCT");
    }

    @Test @DisplayName("CCLI/CCDL SEND-PLAIN-TEXT / SEND-LONG-TEXT / ABEND-ROUTINE: diagnostic reachability")
    void diagnosticHelpersAreNotBusinessScreens() throws Exception {
        for (String program : new String[]{"COCRDLIC", "COCRDSLC"}) {
            assertThat(source(program)).contains("SEND-LONG-TEXT.", "SEND-PLAIN-TEXT.")
                    .doesNotContain("PERFORM SEND-LONG-TEXT");
        }
        assertThat(source("COCRDLIC")).doesNotContain("PERFORM SEND-PLAIN-TEXT");
        assertThat(source("COCRDSLC")).contains("PERFORM SEND-PLAIN-TEXT", "UNEXPECTED DATA SCENARIO",
                "LABEL(ABEND-ROUTINE)", "UNEXPECTED ABEND OCCURRED.", "ABCODE('9999')");
    }
}
