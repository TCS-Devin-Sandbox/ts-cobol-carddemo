package com.carddemo.interest.pilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes a committed run in the comparator's actual-directory layout: the SYSTRAN generation, the
 * rewritten ACCTDATA KSDS image, STEP15 restricted SYSOUT and the step return code. Pilot field slice
 * only: the setup-job step codes the golden rc.json also lists are not produced (no bridge).
 */
@Component
public class PilotExporter {

    static final String START = "START OF EXECUTION OF PROGRAM CBACT04C";
    static final String END = "END OF EXECUTION OF PROGRAM CBACT04C";
    private final PilotStore store;

    public PilotExporter(PilotStore store) {
        this.store = store;
    }

    public void export(UUID runId, Path outputDir) throws IOException {
        FixtureDatasets.writeFixed(outputDir.resolve("datasets").resolve(FixtureDatasets.SYSTRAN_DSN + ".txt"),
                store.transactionRecordsInEmitOrder(runId));
        List<byte[]> accounts = new ArrayList<>();
        for (Account account : store.accountsInKeyOrder(runId)) {
            accounts.add(store.findDelta(runId, account.acctId()).map(account::rewrittenWith)
                    .orElseGet(account::rawRecord));
        }
        FixtureDatasets.writeFixed(outputDir.resolve("datasets").resolve(FixtureDatasets.ACCOUNT_DSN + ".txt"), accounts);
        List<String> sysout = new ArrayList<>();
        sysout.add(START);
        sysout.addAll(store.sysoutInOrder(runId));
        sysout.add(END);
        Path sysoutFile = outputDir.resolve("sysout").resolve("INTCALC.STEP15.txt");
        Files.createDirectories(sysoutFile.getParent());
        Files.writeString(sysoutFile, String.join("\n", sysout) + "\n", StandardCharsets.ISO_8859_1);
        writeReturnCode(outputDir, 0);
    }

    static void writeReturnCode(Path outputDir, int rc) throws IOException {
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("rc.json"), """
                {
                 "INTCALC": {
                  "maxrc": %d,
                  "steps": {
                   "STEP15": %d
                  }
                 }
                }""".formatted(rc, rc));
    }
}
