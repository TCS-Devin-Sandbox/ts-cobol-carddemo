package com.carddemo.batch.common.ims;

import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;

/** RDW-framed DBPAUTP0 unloads in the format supplied with CardDemo. */
public final class Dfsurgu0Unload {
    private static final Charset IBM037 = Charset.forName("IBM037");
    private static final int RDW_LENGTH = 4;
    private static final int PREFIX_LENGTH = 35;
    private static final int METADATA_LENGTH = 84;

    public enum SegmentType {
        PAUTSUM0(CardDemoLayout.CIPAUSMY, 1),
        PAUTDTL1(CardDemoLayout.CIPAUDTY, 2);

        private final CardDemoLayout layout;
        private final int level;

        SegmentType(CardDemoLayout layout, int level) {
            this.layout = layout;
            this.level = level;
        }

        private int bodyLength() {
            return PREFIX_LENGTH + layout.length() + 1;
        }

        private boolean matchesPrefix(byte[] prefix) {
            return prefix.length == PREFIX_LENGTH && prefix[0] == level
                    && unsignedShort(prefix, 4) == layout.length()
                    && new String(prefix, 6, 8, IBM037).equals(name());
        }
    }

    public sealed interface Frame permits Metadata, Segment {}

    /** Opaque header/footer bytes, excluding the RDW. */
    public record Metadata(byte[] body) implements Frame {
        public Metadata {
            body = body.clone();
            if (body.length != METADATA_LENGTH || body[0] != 0
                    || (body[1] != (byte) 0x80 && body[1] != (byte) 0x98)) {
                throw new IllegalArgumentException("Invalid DBPAUTP0 metadata frame");
            }
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    /** Preserves the 35 prefix bytes and the one byte after the copybook payload. */
    public record Segment(SegmentType type, byte[] prefix, Map<String, Object> fields, byte suffix) implements Frame {
        public Segment {
            prefix = prefix.clone();
            if (!type.matchesPrefix(prefix)) throw new IllegalArgumentException("Invalid " + type + " prefix");
            fields = Map.copyOf(fields);
        }

        @Override
        public byte[] prefix() {
            return prefix.clone();
        }
    }

    private final FixedRecordCodec summary;
    private final FixedRecordCodec detail;

    public Dfsurgu0Unload() throws IOException {
        summary = CardDemoLayout.CIPAUSMY.ebcdicCodec();
        detail = CardDemoLayout.CIPAUDTY.ebcdicCodec();
    }

    /** Reads one frame, or null on clean EOF. The stream remains caller-owned. */
    public Frame readRecord(InputStream in) throws IOException {
        byte[] rdw = in.readNBytes(RDW_LENGTH);
        if (rdw.length == 0) return null;
        if (rdw.length != RDW_LENGTH) throw new EOFException("Truncated RDW");
        if (rdw[2] != 0 || rdw[3] != 0) throw new IOException("Invalid DBPAUTP0 RDW flags");
        int length = unsignedShort(rdw, 0) - RDW_LENGTH;
        SegmentType type = null;
        if (length == SegmentType.PAUTSUM0.bodyLength()) {
            type = SegmentType.PAUTSUM0;
        } else if (length == SegmentType.PAUTDTL1.bodyLength()) {
            type = SegmentType.PAUTDTL1;
        } else if (length != METADATA_LENGTH) {
            throw new IOException("Unsupported DBPAUTP0 record length: " + (length + RDW_LENGTH));
        }
        byte[] body = in.readNBytes(length);
        if (body.length != length) throw new EOFException("Truncated DBPAUTP0 record");
        try {
            if (type == null) return new Metadata(body);
            byte[] prefix = Arrays.copyOf(body, PREFIX_LENGTH);
            byte[] payload = Arrays.copyOfRange(body, PREFIX_LENGTH, length - 1);
            return new Segment(type, prefix, codec(type).decode(payload), body[length - 1]);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid DBPAUTP0 record: " + e.getMessage(), e);
        }
    }

    /** Writes one frame without closing or flushing the caller-owned stream. */
    public void writeRecord(OutputStream out, Frame frame) throws IOException {
        switch (frame) {
            case Metadata metadata -> {
                writeRdw(out, METADATA_LENGTH);
                out.write(metadata.body);
            }
            case Segment segment -> {
                byte[] payload = codec(segment.type()).encode(segment.fields());
                writeRdw(out, segment.type().bodyLength());
                out.write(segment.prefix);
                out.write(payload);
                out.write(segment.suffix());
            }
        }
    }

    private FixedRecordCodec codec(SegmentType type) {
        return type == SegmentType.PAUTSUM0 ? summary : detail;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset]) << 8) | Byte.toUnsignedInt(bytes[offset + 1]);
    }

    private static void writeRdw(OutputStream out, int bodyLength) throws IOException {
        int length = bodyLength + RDW_LENGTH;
        out.write(new byte[] {(byte) (length >>> 8), (byte) length, 0, 0});
    }
}
