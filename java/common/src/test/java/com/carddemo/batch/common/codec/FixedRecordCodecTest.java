package com.carddemo.batch.common.codec;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.carddemo.batch.common.codec.RecordLayout.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class FixedRecordCodecTest {
    @Test
    void ebcdicTextRoundTripsEveryByte() {
        var codec = new FixedRecordCodec(layout(TEXT, 256, 256, 0, false), Charset.forName("IBM037"));
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)));
    }

    static Stream<Arguments> signs() {
        return Stream.of(StandardCharsets.ISO_8859_1, Charset.forName("IBM037")).flatMap(charset ->
                IntStream.range(0, 20).mapToObj(i -> Arguments.of(charset, i % 10, i >= 10)));
    }

    @ParameterizedTest
    @MethodSource("signs")
    void overpunchEveryDigitAndSign(Charset charset, int digit, boolean negative) {
        var codec = new FixedRecordCodec(layout(DISPLAY, 3, 3, 2, true), charset);
        String raw = "12" + (negative ? "}JKLMNOPQR" : "{ABCDEFGHI").charAt(digit);
        BigDecimal expected = new BigDecimal((negative ? "-" : "") + "1.2" + digit);
        assertEquals(expected, codec.decode(raw.getBytes(charset)).get("VALUE"));
        assertArrayEquals(raw.getBytes(charset), codec.encode(Map.of("VALUE", expected)));
    }

    static Stream<Arguments> packed() {
        return Stream.of(Arguments.of("12345C", "123.45", true, 5),
                Arguments.of("12345D", "-123.45", true, 5),
                Arguments.of("12345F", "123.45", false, 5),
                Arguments.of("01234C", "12.34", true, 4),
                Arguments.of("00000C", "0.00", true, 5));
    }

    @ParameterizedTest
    @MethodSource("packed")
    void packedDecimal(String hex, String value, boolean signed, int digits) {
        var codec = new FixedRecordCodec(layout(COMP3, 3, digits, 2, signed));
        byte[] raw = HexFormat.of().parseHex(hex);
        BigDecimal decimal = new BigDecimal(value);
        assertEquals(decimal, codec.decode(raw).get("VALUE"));
        assertArrayEquals(raw, codec.encode(Map.of("VALUE", decimal)));
    }

    static Stream<Arguments> binary() {
        return Stream.of(Arguments.of("FFFF", "-1", true, 4, 0),
                Arguments.of("270F", "9999", true, 4, 0),
                Arguments.of("FFFF", "65535", false, 5, 0),
                Arguments.of("FFFFFFFF", "-0.01", true, 9, 2),
                Arguments.of("7FFFFFFFFFFFFFFF", "9223372036854775807", true, 19, 0),
                Arguments.of("8000000000000000", "-9223372036854775808", true, 19, 0),
                Arguments.of("FFFFFFFFFFFFFFFF", "18446744073709551615", false, 20, 0));
    }

    @ParameterizedTest
    @MethodSource("binary")
    void bigEndianBinary(String hex, String value, boolean signed, int digits, int scale) {
        byte[] raw = HexFormat.of().parseHex(hex);
        var codec = new FixedRecordCodec(layout(COMP, raw.length, digits, scale, signed));
        BigDecimal decimal = new BigDecimal(value);
        assertEquals(decimal, codec.decode(raw).get("VALUE"));
        assertArrayEquals(raw, codec.encode(Map.of("VALUE", decimal)));
    }

    @Test
    void rejectsInvalidAndOverflowingValuesRatherThanTruncating() {
        var display = new FixedRecordCodec(layout(DISPLAY, 3, 3, 2, false));
        assertThrows(IllegalArgumentException.class, () -> display.decode(new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> display.decode("1?2".getBytes(StandardCharsets.ISO_8859_1)));
        assertThrows(IllegalArgumentException.class, () -> display.encode(Map.of("VALUE", new BigDecimal("10.00"))));
        assertThrows(IllegalArgumentException.class, () -> display.encode(Map.of("VALUE", new BigDecimal("-1.00"))));
        assertThrows(ArithmeticException.class, () -> display.encode(Map.of("VALUE", new BigDecimal("1.001"))));
        assertThrows(IllegalArgumentException.class, () -> display.encode(Map.of()));
        var packed = new FixedRecordCodec(layout(COMP3, 2, 3, 0, true));
        assertThrows(IllegalArgumentException.class, () -> packed.decode(HexFormat.of().parseHex("1A2C")));
        assertThrows(IllegalArgumentException.class, () -> packed.decode(HexFormat.of().parseHex("1234")));
        var binary = new FixedRecordCodec(layout(COMP, 2, 5, 0, true));
        assertThrows(IllegalArgumentException.class, () -> binary.encode(Map.of("VALUE", new BigDecimal("32768"))));
    }

    @Test
    void spaceFilledAndPartiallySpacedNumericsFollowPythonZeroSemantics() {
        var codec = new FixedRecordCodec(layout(DISPLAY, 3, 3, 2, true));
        assertEquals(new BigDecimal("0.00"), codec.decode("   ".getBytes(StandardCharsets.ISO_8859_1)).get("VALUE"));
        assertEquals(new BigDecimal("1.02"), codec.decode("1 B".getBytes(StandardCharsets.ISO_8859_1)).get("VALUE"));
        assertArrayEquals("00{".getBytes(StandardCharsets.ISO_8859_1), codec.encode(Map.of("VALUE", BigDecimal.ZERO)));
    }

    @Test
    void textPreservesFillerAndSpacesAndPadsWithoutTruncation() {
        var codec = new FixedRecordCodec(layout(TEXT, 4, 4, 0, false));
        assertEquals("A   ", codec.decode(codec.encode(Map.of("VALUE", "A"))).get("VALUE"));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(Map.of("VALUE", "ABCDE")));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(Map.of("VALUE", "\u20ac")));
    }

    @Test
    void parserRejectsUnsupportedClausesAndMissingCopybooks() throws Exception {
        assertThrows(java.nio.file.NoSuchFileException.class,
                () -> RecordLayout.fromCopybook(FixtureRoundTripTest.ROOT.resolve("absent.cpy")));
        assertThrows(IllegalArgumentException.class, () -> RecordLayout.fromCopybook(
                FixtureRoundTripTest.ROOT.resolve("app/cpy/CVTRA07Y.cpy")));
    }

    private static RecordLayout layout(RecordLayout.Kind kind, int length, int digits, int scale, boolean signed) {
        return new RecordLayout(List.of(new RecordLayout.Field("VALUE", kind, length, digits, scale, signed)));
    }
}
