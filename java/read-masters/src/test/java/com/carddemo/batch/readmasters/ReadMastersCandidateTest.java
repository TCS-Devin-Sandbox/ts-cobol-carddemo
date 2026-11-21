package com.carddemo.batch.readmasters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ReadMastersCandidateTest {
    @TempDir Path temp;
    private static final String PREFIX = "AWS.M2.CARDDEMO.";

    private static Path fixtures() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("fixtures/read-masters"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Cannot find repository fixtures");
        return root.resolve("fixtures/read-masters");
    }

    @Test void populatedAndEmptyMatchEveryGoldenArtifact() throws Exception {
        for (String name : new String[]{"r01-populated", "r02-empty"}) {
            Path fixture = fixtures().resolve(name);
            Path out = temp.resolve(name);
            ReadMastersCandidate.run(fixture.resolve("input"), out);
            try (var files = Files.walk(fixture.resolve("expected"))) {
                for (Path expected : files.filter(Files::isRegularFile).toList()) {
                    Path relative = fixture.resolve("expected").relativize(expected);
                    if (relative.toString().equals("manifest.json")) continue;
                    if (relative.toString().equals("rc.json")) {
                        assertEquals(Files.readString(expected).replaceAll("\\s", ""),
                                Files.readString(out.resolve(relative)).replaceAll("\\s", ""));
                    } else assertArrayEquals(Files.readAllBytes(expected), Files.readAllBytes(out.resolve(relative)),
                            name + "/" + relative);
                }
            }
        }
    }

    @Test void indexedReadsSortAndSuppressDuplicateKeys() throws Exception {
        Path input = copyInput();
        Path file = input.resolve(PREFIX + "CARDDATA.PS.txt");
        var rows = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        Files.writeString(file, rows.getLast() + "\n" + rows.getFirst() + "\n" + rows.getLast() + "\n",
                StandardCharsets.ISO_8859_1);
        ReadMastersCandidate.run(input, temp.resolve("out"));
        assertEquals("START OF EXECUTION OF PROGRAM CBACT02C\n" + pad(rows.getFirst(), 150) + "\n" + pad(rows.getLast(), 150)
                + "\nEND OF EXECUTION OF PROGRAM CBACT02C\n",
                Files.readString(temp.resolve("out/sysout/READCARD.STEP05.txt"), StandardCharsets.ISO_8859_1));
    }

    @Test void packedDebitRetainsPriorValueWhenSourceDebitIsNonzero() throws Exception {
        Path input = copyInput();
        Path file = input.resolve(PREFIX + "ACCTDATA.PS.txt");
        var rows = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        String second = rows.get(1);
        second = second.substring(0, 90) + "00000000010{" + second.substring(102);
        Files.writeString(file, rows.getFirst() + "\n" + second + "\n", StandardCharsets.ISO_8859_1);
        ReadMastersCandidate.run(input, temp.resolve("out"));
        var exported = Files.readAllLines(temp.resolve("out/datasets/" + PREFIX + "ACCTDATA.PSCOMP.txt"));
        byte[] record = HexFormat.of().parseHex(exported.get(1));
        assertEquals("0000000252500c", HexFormat.of().formatHex(record, 90, 97));
    }

    @Test void missingFileAbendsOnlyThatJobAndKeepsOtherJobsRunning() throws Exception {
        Path input = copyInput();
        Files.delete(input.resolve(PREFIX + "CARDDATA.PS.txt"));
        Path out = temp.resolve("out");
        ReadMastersCandidate.run(input, out);
        assertEquals("START OF EXECUTION OF PROGRAM CBACT02C\nERROR OPENING CARDFILE\n"
                + "FILE STATUS IS: NNNN0035\nABENDING PROGRAM\nCEE3ABD: USER ABEND U+000000999\n",
                Files.readString(out.resolve("sysout/READCARD.STEP05.txt")));
        assertTrue(Files.readString(out.resolve("rc.json")).contains("\"STEP05\": 12"));
        assertTrue(Files.readString(out.resolve("sysout/READCUST.STEP05.txt"))
                .endsWith("END OF EXECUTION OF PROGRAM CBCUS01C\n"));
    }

    @Test void fixtureLinesArePaddedAndTruncatedLikeTheHarness() throws Exception {
        Path input = copyInput();
        Files.writeString(input.resolve(PREFIX + "CARDDATA.PS.txt"), "short\n" + "z".repeat(160) + "\n");
        ReadMastersCandidate.run(input, temp.resolve("out"));
        String text = Files.readString(temp.resolve("out/sysout/READCARD.STEP05.txt"));
        assertEquals("START OF EXECUTION OF PROGRAM CBACT02C\n" + pad("short", 150) + "\n"
                + "z".repeat(150) + "\nEND OF EXECUTION OF PROGRAM CBACT02C\n", text);
    }

    @Test void rejectsWrongChainAndMalformedArguments() {
        assertThrows(IllegalArgumentException.class, () -> ReadMastersCandidate.main(new String[]{"--chain"}));
        assertThrows(IllegalArgumentException.class, () -> ReadMastersCandidate.main(new String[]{"--chain", "posting"}));
        assertThrows(IllegalArgumentException.class, () -> ReadMastersCandidate.main(new String[]{"--chain", "read-masters"}));
    }

    private Path copyInput() throws Exception {
        Path input = Files.createDirectory(temp.resolve("input"));
        try (var files = Files.list(fixtures().resolve("r01-populated/input"))) {
            for (Path file : files.toList()) Files.copy(file, input.resolve(file.getFileName()));
        }
        return input;
    }

    private static String pad(String value, int length) {
        return (value + " ".repeat(length)).substring(0, length);
    }
}
