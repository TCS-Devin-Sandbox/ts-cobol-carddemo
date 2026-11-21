package com.carddemo.batch.common.legacy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/** Formats documented for COBDATFT in docs/LEGACY-LANGUAGES.md and its source, app/asm/COBDATFT.asm. */
class CobDatFtTest {
    private static final String BLANK_MESSAGE = " ".repeat(CodatecnRecord.MESSAGE_LENGTH);

    @Test
    void recordMatchesCopybookAndDsect() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("app/cpy/CODATECN.cpy"))) root = root.getParent();
        var pic = Pattern.compile("^\\s+(05|10)\\s+(CODATECN-[A-Z0-9-]+)\\s+PIC\\s+X(?:\\((\\d+)\\))?\\.");
        List<Integer> lengths = new ArrayList<>();
        for (String line : Files.readAllLines(root.resolve("app/cpy/CODATECN.cpy"))) {
            var match = pic.matcher(line);
            if (match.find()) lengths.add(match.group(3) == null ? 1 : Integer.parseInt(match.group(3)));
        }
        assertEquals(List.of(1, 20, 1, 20, 38), lengths);
        String dsect = Files.readString(root.resolve("app/maclib/COCDATFT.mac"));
        for (String field : List.of("COINTYPE +DS +CL1 ", "COINPDT +DS +CL20 ", "COOUTYPE +DS +CL1 ",
                "COOUTDT +DS +CL20 ", "COERMSG +DS +CL38 ")) {
            assertTrue(Pattern.compile(field).matcher(dsect).find(), field);
        }
        assertEquals(80, CodatecnRecord.LENGTH);
    }

    static Stream<Arguments> conversions() {
        return Stream.of(
                // type 1 YYYYMMDD -> YYYY-MM-DD for every output type except 2
                Arguments.of('1', "20220705", ' ', "2022-07-05"),
                Arguments.of('1', "20220705", '1', "2022-07-05"),
                Arguments.of('1', "19991231", 'X', "1999-12-31"),
                // type 2 YYYY-MM-DD -> YYYYMMDD for every output type except 1
                Arguments.of('2', "2022-07-05", '2', "20220705"),
                Arguments.of('2', "2022-07-05", ' ', "20220705"),
                Arguments.of('2', "2000-02-29", 'X', "20000229"),
                // VALIDIN2 hyphen check is commented out: any separator is accepted
                Arguments.of('2', "2022/07/05", '2', "20220705"),
                Arguments.of('2', "2022.07.05", '2', "20220705"),
                // no calendar validation: characters are moved, not parsed
                Arguments.of('1', "20221399", ' ', "2022-13-99"),
                Arguments.of('2', "ABCD-EF-GH", '2', "ABCDEFGH"),
                // VALIDIN1's hyphen check compares 20 bytes and never matches a date
                Arguments.of('1', "2022-07-05", ' ', "2022--0-7-"));
    }

    @ParameterizedTest(name = "type {0} {1} out {2} -> {3}")
    @MethodSource("conversions")
    void documentedConversions(char type, String input, char outputType, String expected) {
        var result = CobDatFt.call(CodatecnRecord.request(type, input, outputType));
        assertEquals(pad(expected, 20), result.outputDate());
        assertEquals(BLANK_MESSAGE, result.errorMessage());
        assertEquals(CodatecnRecord.request(type, input, outputType).inputDate(), result.inputDate());
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of('1', "20220705", '2'), // YYYYMMDD requested as YYYYMMDD
                Arguments.of('2', "2022-07-05", '1'), // YYYY-MM-DD requested as YYYY-MM-DD
                Arguments.of('3', "20220705", ' '),
                Arguments.of(' ', "20220705", '1'),
                Arguments.of('0', "2022-07-05", '2'));
    }

    @ParameterizedTest(name = "type {0} {1} out {2} -> INVALID INPUT")
    @MethodSource("invalidInputs")
    void mismatchedOrUnknownTypesSetInvalidInputAndLeaveOutputDate(char type, String input, char outputType) {
        var before = new CodatecnRecord(String.valueOf(type), input, String.valueOf(outputType),
                "PREVIOUS-OUTPUT-DATE", "");
        var result = CobDatFt.call(before);
        assertEquals(pad("INVALID INPUT", 38), result.errorMessage());
        assertEquals("PREVIOUS-OUTPUT-DATE", result.outputDate());
    }

    @Test
    void cbact01cReissueDateCall() {
        // CBACT01C 1300-POPUL-ACCT-RECORD: ACCT-REISSUE-DATE (YYYY-MM-DD), types '2'/'2', then MOVE
        // CODATECN-0UT-DATE TO OUT-ACCT-REISSUE-DATE PIC X(10).
        var result = CobDatFt.call(CodatecnRecord.request('2', "2025-11-30", '2'));
        assertEquals("20251130  ", result.outputDate().substring(0, 10));
    }

    @Test
    void untouchedBytesAndMessageSurviveSuccessfulCall() {
        var type1 = CobDatFt.call(new CodatecnRecord("1", "20220705", " ", "AAAAAAAAAABBBBBBBBBB", "INVALID INPUT"));
        assertEquals("2022-07-05BBBBBBBBBB", type1.outputDate());
        assertEquals(pad("INVALID INPUT", 38), type1.errorMessage());
        var type2 = CobDatFt.call(new CodatecnRecord("2", "2022-07-05", "2", "AAAAAAAAAABBBBBBBBBB", ""));
        assertEquals("20220705AABBBBBBBBBB", type2.outputDate());
    }

    @Test
    void byteImageRoundTrip() {
        String image = "2" + pad("2022-07-05", 20) + "2" + " ".repeat(20) + BLANK_MESSAGE;
        String result = CobDatFt.call(image);
        assertEquals(80, result.length());
        assertEquals("2" + pad("2022-07-05", 20) + "2" + pad("20220705", 20) + BLANK_MESSAGE, result);
        assertThrows(IllegalArgumentException.class, () -> CobDatFt.call(image.substring(1)));
        assertThrows(IllegalArgumentException.class, () -> CodatecnRecord.request('2', "x".repeat(21), '2'));
    }

    @Test
    void javaTimeLayoutsAgreeWithRoutineForValidDates() {
        List<LocalDate> dates = new ArrayList<>(List.of(LocalDate.of(1, 1, 1), LocalDate.of(1900, 2, 28),
                LocalDate.of(2000, 2, 29), LocalDate.of(9999, 12, 31)));
        for (var day = LocalDate.of(2024, 1, 1); day.getYear() == 2024; day = day.plusDays(1)) dates.add(day);
        for (LocalDate date : dates) {
            String basic = CobDatFt.Layout.YYYYMMDD.format(date);
            String iso = CobDatFt.Layout.YYYY_MM_DD.format(date);
            assertEquals(pad(iso, 20), CobDatFt.call(CodatecnRecord.request('1', basic, ' ')).outputDate());
            assertEquals(pad(basic, 20), CobDatFt.call(CodatecnRecord.request('2', iso, '2')).outputDate());
            assertEquals(date, CobDatFt.Layout.YYYYMMDD.parse(basic));
            assertEquals(date, CobDatFt.Layout.YYYY_MM_DD.parse(iso));
        }
    }

    private static String pad(String value, int length) {
        return value + " ".repeat(length - value.length());
    }
}
