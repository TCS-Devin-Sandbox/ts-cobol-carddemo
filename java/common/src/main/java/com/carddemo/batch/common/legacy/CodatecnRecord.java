package com.carddemo.batch.common.legacy;

/**
 * {@code CODATECN-REC} (app/cpy/CODATECN.cpy, DSECT {@code COCDATFT}): the 80-byte parameter record of
 * {@code COBDATFT}. Every field is PIC X; values are space-padded to their fixed width.
 */
public record CodatecnRecord(String type, String inputDate, String outputType, String outputDate, String errorMessage) {
    public static final int TYPE_LENGTH = 1;
    public static final int DATE_LENGTH = 20;
    public static final int MESSAGE_LENGTH = 38;
    public static final int LENGTH = TYPE_LENGTH + DATE_LENGTH + TYPE_LENGTH + DATE_LENGTH + MESSAGE_LENGTH;

    public CodatecnRecord {
        type = fixed("CODATECN-TYPE", type, TYPE_LENGTH);
        inputDate = fixed("CODATECN-INP-DATE", inputDate, DATE_LENGTH);
        outputType = fixed("CODATECN-OUTTYPE", outputType, TYPE_LENGTH);
        outputDate = fixed("CODATECN-0UT-DATE", outputDate, DATE_LENGTH);
        errorMessage = fixed("CODATECN-ERROR-MSG", errorMessage, MESSAGE_LENGTH);
    }

    /** A record as {@code CBACT01C} builds it: storage initialised to spaces, then type, date and output type moved in. */
    public static CodatecnRecord request(char type, String inputDate, char outputType) {
        return new CodatecnRecord(String.valueOf(type), inputDate, String.valueOf(outputType), "", "");
    }

    public static CodatecnRecord parse(String image) {
        if (image.length() != LENGTH) {
            throw new IllegalArgumentException("CODATECN-REC is " + LENGTH + " bytes, got " + image.length());
        }
        int out = TYPE_LENGTH + DATE_LENGTH;
        int message = out + TYPE_LENGTH + DATE_LENGTH;
        return new CodatecnRecord(image.substring(0, TYPE_LENGTH), image.substring(TYPE_LENGTH, out),
                image.substring(out, out + TYPE_LENGTH), image.substring(out + TYPE_LENGTH, message),
                image.substring(message));
    }

    public String image() {
        return type + inputDate + outputType + outputDate + errorMessage;
    }

    CodatecnRecord withOutputDate(String date) {
        return new CodatecnRecord(type, inputDate, outputType, date, errorMessage);
    }

    CodatecnRecord withErrorMessage(String message) {
        return new CodatecnRecord(type, inputDate, outputType, outputDate, message);
    }

    private static String fixed(String field, String value, int length) {
        if (value == null) value = "";
        if (value.length() > length) {
            throw new IllegalArgumentException(field + " is PIC X(" + length + "), got " + value.length() + " characters");
        }
        return value + " ".repeat(length - value.length());
    }
}
