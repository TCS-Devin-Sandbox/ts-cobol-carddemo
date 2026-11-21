package com.carddemo.batch.statements;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** CREASTMT STEP010: SORT FIELDS=(263,16,CH,A,1,16,CH,A), OUTREC FIELDS=(1:263,16,17:1,262,279:279,50). */
final class TransactionSort {
    static final int LRECL = 350;

    private TransactionSort() { }

    static List<String> sortAndReformat(List<String> transactRecords) {
        var records = new ArrayList<byte[]>();
        for (String record : transactRecords) records.add(pad(record.getBytes(StandardCharsets.ISO_8859_1)));
        Comparator<byte[]> byCard = (a, b) -> Arrays.compareUnsigned(a, 262, 278, b, 262, 278);
        records.sort(byCard.thenComparing((a, b) -> Arrays.compareUnsigned(a, 0, 16, b, 0, 16)));
        var out = new ArrayList<String>(records.size());
        for (byte[] in : records) {
            byte[] record = new byte[LRECL];
            Arrays.fill(record, (byte) ' ');
            System.arraycopy(in, 262, record, 0, 16);
            System.arraycopy(in, 0, record, 16, 262);
            System.arraycopy(in, 278, record, 278, 50);
            out.add(new String(record, StandardCharsets.ISO_8859_1));
        }
        return out;
    }

    private static byte[] pad(byte[] record) {
        if (record.length > LRECL) throw new IllegalArgumentException("TRANSACT record longer than " + LRECL);
        byte[] padded = Arrays.copyOf(record, LRECL);
        Arrays.fill(padded, record.length, LRECL, (byte) ' ');
        return padded;
    }
}
