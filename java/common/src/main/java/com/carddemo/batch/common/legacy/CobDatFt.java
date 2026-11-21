package com.carddemo.batch.common.legacy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Java replacement for the HLASM date-format routine {@code COBDATFT} (app/asm/COBDATFT.asm), called by
 * {@code CBACT01C} as {@code CALL 'COBDATFT' USING CODATECN-REC}.
 *
 * <p>{@link #call} reproduces the routine byte for byte: it moves characters without validating the date, leaves
 * the bytes of {@code CODATECN-0UT-DATE} it does not write unchanged, never clears {@code CODATECN-ERROR-MSG}, and
 * always returns RC 0 ({@code SR R15,R15}). See docs/LEGACY-LANGUAGES.md for the quirks. New code that holds a
 * {@link LocalDate} uses {@link Layout} instead; for valid dates both give the same characters.
 */
public final class CobDatFt {
    public static final int RETURN_CODE = 0;
    public static final String INVALID_INPUT = "INVALID INPUT";

    /** The two date layouts the routine converts between, as {@code java.time} formatters. */
    public enum Layout {
        /** {@code YYYYMMDD}: input type {@code 1}, produced for any output type except {@code 1} from input type {@code 2}. */
        YYYYMMDD(DateTimeFormatter.BASIC_ISO_DATE),
        /** {@code YYYY-MM-DD}: input type {@code 2}, produced for any output type except {@code 2} from input type {@code 1}. */
        YYYY_MM_DD(DateTimeFormatter.ISO_LOCAL_DATE);

        private final DateTimeFormatter formatter;

        Layout(DateTimeFormatter formatter) {
            this.formatter = formatter;
        }

        public String format(LocalDate date) {
            return formatter.format(date);
        }

        public LocalDate parse(String text) {
            return LocalDate.parse(text, formatter);
        }
    }

    private CobDatFt() {
    }

    /** Applies {@code COBDATFT} to its parameter record and returns the record as the caller sees it afterwards. */
    public static CodatecnRecord call(CodatecnRecord record) {
        String in = record.inputDate();
        String out = record.outputDate();
        char outputType = record.outputType().charAt(0);
        return switch (record.type().charAt(0)) {
            // VALIDIN1. Its CLC COINPDT+4,=C'-' compares L'COINPDT = 20 bytes with the literal pool, so it never
            // matches a date and a hyphenated input is not rejected.
            case '1' -> outputType == '2' ? invalid(record)
                    : record.withOutputDate(in.substring(0, 4) + '-' + in.substring(4, 6) + '-' + in.substring(6, 8)
                            + out.substring(10));
            // VALIDIN2. The separator check is commented out: any byte at positions 5 and 8 is accepted.
            case '2' -> outputType == '1' ? invalid(record)
                    : record.withOutputDate(in.substring(0, 4) + in.substring(5, 7) + in.substring(8, 10)
                            + out.substring(8));
            default -> invalid(record);
        };
    }

    /** Applies {@code COBDATFT} to the 80-byte image of {@code CODATECN-REC}. */
    public static String call(String image) {
        return call(CodatecnRecord.parse(image)).image();
    }

    private static CodatecnRecord invalid(CodatecnRecord record) {
        // MVC COERMSG,=C'INVALID INPUT' moves L'COERMSG = 38 bytes; on the host the 25 after the literal are
        // whatever follows it in the load module. Off-host they are spaces.
        return record.withErrorMessage(INVALID_INPUT);
    }
}
