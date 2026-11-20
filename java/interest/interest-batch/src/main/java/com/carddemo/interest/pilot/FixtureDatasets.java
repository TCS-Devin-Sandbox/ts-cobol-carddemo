package com.carddemo.interest.pilot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Frozen fixture path convention: {@code <input>/AWS.M2.CARDDEMO.<NAME>.PS.txt} (the REPRO sources of the
 * INTCALC setup jobs) and {@code <output>/datasets/<DSN>.txt}. Lab framing: one record per LF-terminated
 * line, short lines space-padded to LRECL (the setup REPRO pads; the comparator re-pads too).
 */
public final class FixtureDatasets {

    public static final String SYSTRAN_DSN = "AWS.M2.CARDDEMO.SYSTRAN.G0001V00";
    public static final String ACCOUNT_DSN = "AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS";

    private FixtureDatasets() {
    }

    public static Path input(Path inputDir, String name) {
        return inputDir.resolve("AWS.M2.CARDDEMO." + name + ".PS.txt");
    }

    public static List<byte[]> readFixed(Path file, String dataset, int lrecl) {
        byte[] all;
        try {
            all = Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw new PilotFailure.UnreadableInput(dataset, 0, 0, "dataset missing");
        } catch (IOException e) {
            throw new PilotFailure.UnreadableInput(dataset, 0, 0, "read failed: " + e.getClass().getSimpleName());
        }
        List<byte[]> records = new ArrayList<>();
        int start = 0;
        long recordNumber = 0;
        while (start < all.length) {
            int end = start;
            while (end < all.length && all[end] != '\n') {
                end++;
            }
            recordNumber++;
            int length = end - start;
            if (length > lrecl) {
                throw new PilotFailure.UnreadableInput(dataset, recordNumber, lrecl, "record longer than LRECL");
            }
            byte[] record = new byte[lrecl];
            Arrays.fill(record, (byte) ' ');
            System.arraycopy(all, start, record, 0, length);
            records.add(record);
            start = end + 1;
        }
        return records;
    }

    /** Lab output framing: trailing spaces dropped per line, LF-terminated (matches the recorder). */
    public static void writeFixed(Path file, List<byte[]> records) throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder out = new StringBuilder();
        for (byte[] record : records) {
            int end = record.length;
            while (end > 0 && record[end - 1] == (byte) ' ') {
                end--;
            }
            out.append(new String(record, 0, end, PilotFieldCodec.LAB_PROFILE)).append('\n');
        }
        Files.write(file, out.toString().getBytes(PilotFieldCodec.LAB_PROFILE));
    }
}
