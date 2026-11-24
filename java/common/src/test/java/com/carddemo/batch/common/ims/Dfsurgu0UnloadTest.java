package com.carddemo.batch.common.ims;

import com.carddemo.batch.common.codec.FixedRecordCodec.BlankNumeric;
import com.carddemo.batch.common.ims.Dfsurgu0Unload.Metadata;
import com.carddemo.batch.common.ims.Dfsurgu0Unload.Segment;
import com.carddemo.batch.common.ims.Dfsurgu0Unload.SegmentType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Dfsurgu0UnloadTest {
    private static final Path UNLOAD = Path.of("../../app/app-authorization-ims-db2-mq/data/EBCDIC",
            "AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat");

    @Test
    void decodesAndReencodesTheSuppliedUnloadByteForByte() throws Exception {
        byte[] fixture = Files.readAllBytes(UNLOAD);
        assertEquals(51_736, fixture.length);
        var codec = new Dfsurgu0Unload();
        var out = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream(fixture);
        int roots = 0;
        int details = 0;
        int metadata = 0;
        int blankRoots = 0;
        Dfsurgu0Unload.Frame frame;
        while ((frame = codec.readRecord(in)) != null) {
            if (frame instanceof Metadata) {
                metadata++;
            } else if (frame instanceof Segment segment) {
                var fields = segment.fields();
                if (segment.type() == SegmentType.PAUTSUM0) {
                    roots++;
                    if (roots == 1) {
                        assertEquals(BigDecimal.ONE, fields.get("PA-ACCT-ID"));
                        assertEquals(BigDecimal.ONE, fields.get("PA-CUST-ID"));
                        assertEquals(new BigDecimal("2022.00"), fields.get("PA-CREDIT-LIMIT"));
                        assertEquals(new BigDecimal("6"), fields.get("PA-APPROVED-AUTH-CNT"));
                    }
                    if (fields.get("PA-ACCT-ID") == BlankNumeric.SPACES) {
                        blankRoots++;
                        assertEquals(new BigDecimal("0"), fields.get("PA-CUST-ID"));
                        assertEquals(BlankNumeric.SPACES, fields.get("PA-APPROVED-AUTH-AMT"));
                        assertEquals(BlankNumeric.SPACES, fields.get("PA-APPROVED-AUTH-CNT"));
                    }
                } else {
                    details++;
                    if (details == 1) {
                        assertEquals(new BigDecimal("76699"), fields.get("PA-AUTH-DATE-9C"));
                        assertEquals(new BigDecimal("998747444"), fields.get("PA-AUTH-TIME-9C"));
                        assertEquals("231027", fields.get("PA-AUTH-ORIG-DATE"));
                        assertEquals(new BigDecimal("1.24"), fields.get("PA-TRANSACTION-AMT"));
                    }
                }
            }
            codec.writeRecord(out, frame);
        }
        assertEquals(22, roots);
        assertEquals(202, details);
        assertEquals(2, metadata);
        assertEquals(1, blankRoots);
        assertArrayEquals(fixture, out.toByteArray());
    }

    @Test
    void writerUsesTheDecodedFieldsAndPreservesFramingBytes() throws Exception {
        byte[] root = Arrays.copyOfRange(Files.readAllBytes(UNLOAD), 88, 228);
        root[139] = (byte) 0xFF;
        var codec = new Dfsurgu0Unload();
        var original = assertInstanceOf(Segment.class, codec.readRecord(new ByteArrayInputStream(root)));
        var updated = new HashMap<>(original.fields());
        updated.put("PA-ACCT-ID", new BigDecimal("2"));

        var out = new ByteArrayOutputStream();
        codec.writeRecord(out, new Segment(original.type(), original.prefix(), updated, original.suffix()));
        root[44] = 0x2C;
        assertArrayEquals(root, out.toByteArray());
    }

    @Test
    void rejectsMalformedOrTruncatedFrames() throws Exception {
        byte[] root = Arrays.copyOfRange(Files.readAllBytes(UNLOAD), 88, 228);
        var codec = new Dfsurgu0Unload();
        assertThrows(EOFException.class, () -> codec.readRecord(new ByteArrayInputStream(Arrays.copyOf(root, 3))));
        assertThrows(EOFException.class, () -> codec.readRecord(new ByteArrayInputStream(Arrays.copyOf(root, 139))));
        assertInvalidByte(codec, root, 1, 4, "record length");
        assertInvalidByte(codec, root, 2, 1, "RDW flags");
        assertInvalidByte(codec, root, 9, 99, "prefix");
        assertInvalidByte(codec, root, 10, 0, "prefix");
        assertInvalidByte(codec, root, 39, 0xA0, "packed digit");
    }

    private static void assertInvalidByte(Dfsurgu0Unload codec, byte[] frame, int offset, int value, String message) {
        byte[] corrupted = frame.clone();
        corrupted[offset] = (byte) value;
        IOException error = assertThrows(IOException.class, () -> codec.readRecord(new ByteArrayInputStream(corrupted)));
        assertTrue(error.getMessage().contains(message), error::getMessage);
    }
}
