package com.carddemo.batch.authdata;

import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.FixedRecordCodec.BlankNumeric;
import com.carddemo.batch.common.codec.RecordLayout;
import com.carddemo.batch.common.ims.Dfsurgu0Unload;
import com.carddemo.batch.common.ims.Dfsurgu0Unload.Metadata;
import com.carddemo.batch.common.ims.Dfsurgu0Unload.Segment;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.carddemo.batch.authdata.AuthRows.DETAIL;
import static com.carddemo.batch.authdata.AuthRows.SUMMARY;
import static com.carddemo.batch.common.ims.Dfsurgu0Unload.SegmentType.PAUTDTL1;
import static com.carddemo.batch.common.ims.Dfsurgu0Unload.SegmentType.PAUTSUM0;

final class AuthTransfer {
    private static final String ACCOUNT_KEY = "PA-ACCT-ID";
    private final Connection db;
    private final Dfsurgu0Unload unload = new Dfsurgu0Unload();
    private final FixedRecordCodec summary = CardDemoLayout.CIPAUSMY.ebcdicCodec();
    private final FixedRecordCodec detail = CardDemoLayout.CIPAUDTY.ebcdicCodec();
    private final FixedRecordCodec key;
    private final int keyLength;

    AuthTransfer(Connection db) throws IOException, SQLException {
        if (db.getAutoCommit()) throw new IllegalStateException("Requires a caller-managed transaction");
        this.db = db;
        var field = CardDemoLayout.CIPAUSMY.load().fields().getFirst();
        key = new FixedRecordCodec(new RecordLayout(List.of(field)), Charset.forName("IBM037"));
        keyLength = field.length();
    }

    void loadUnload(InputStream input) throws IOException, SQLException {
        clear();
        try (var root = SUMMARY.insert(db); var child = DETAIL.insert(db);
             var frame = db.prepareStatement("INSERT INTO pending_auth_unload_frame VALUES (?, ?, ?, ?, ?)")) {
            BigDecimal parent = null;
            long number = 0;
            boolean ended = false;
            for (var record = unload.readRecord(input); record != null; record = unload.readRecord(input)) {
                if (ended || (number == 0 && !(record instanceof Metadata))) {
                    throw new IOException("Expected DBPAUTP0 header, segments, then trailer");
                }
                BigDecimal account = null, date = null, time = null;
                byte[] framing;
                if (record instanceof Metadata metadata) {
                    ended = metadata.body()[1] == (byte) 0x98;
                    if (ended == (number == 0)) throw new IOException("Unexpected DBPAUTP0 metadata");
                    framing = encodeFrame(metadata);
                } else {
                    var segment = (Segment) record;
                    if (segment.type() == PAUTSUM0 && segment.fields().get(ACCOUNT_KEY) == BlankNumeric.SPACES) {
                        parent = null;
                        framing = encodeFrame(segment);
                    } else {
                        framing = segment.prefix();
                        framing = Arrays.copyOf(framing, framing.length + 1);
                        framing[framing.length - 1] = segment.suffix();
                        if (segment.type() == PAUTSUM0) {
                            parent = (BigDecimal) segment.fields().get(ACCOUNT_KEY);
                            account = parent;
                            SUMMARY.write(root, parent, segment.fields());
                        } else {
                            if (parent == null) throw new IOException("Detail has no keyed root");
                            account = parent;
                            date = (BigDecimal) segment.fields().get("PA-AUTH-DATE-9C");
                            time = (BigDecimal) segment.fields().get("PA-AUTH-TIME-9C");
                            DETAIL.write(child, parent, segment.fields());
                        }
                    }
                }
                frame.setLong(1, ++number);
                frame.setBigDecimal(2, account);
                frame.setBigDecimal(3, date);
                frame.setBigDecimal(4, time);
                frame.setBytes(5, framing);
                frame.executeUpdate();
            }
            if (!ended) throw new EOFException("Missing DBPAUTP0 trailer");
        }
    }

    void exportUnload(OutputStream output) throws IOException, SQLException {
        try (var query = db.createStatement();
             var counts = query.executeQuery("""
                     SELECT (SELECT count(*) FROM pending_auth_unload_frame WHERE account_id IS NOT NULL),
                            (SELECT count(*) FROM pending_auth_summary) + (SELECT count(*) FROM pending_auth_detail),
                            EXISTS (SELECT 1 FROM pending_auth_unload_frame)
                     """)) {
            counts.next();
            if (!counts.getBoolean(3) || counts.getLong(1) != counts.getLong(2)) {
                throw new IOException("Unload export requires the imported DFSURGU0 framing and unchanged keys");
            }
        }
        try (var query = db.createStatement()) {
            query.setFetchSize(256);
            try (var rows = query.executeQuery("""
                    SELECT f.account_id, f.auth_date_inv, f.frame_bytes, %s, %s
                    FROM pending_auth_unload_frame f
                    LEFT JOIN pending_auth_summary s ON s.account_id = f.account_id AND f.auth_date_inv IS NULL
                    LEFT JOIN pending_auth_detail d ON (d.account_id, d.auth_date_inv, d.auth_time_inv)
                                                       = (f.account_id, f.auth_date_inv, f.auth_time_inv)
                    ORDER BY f.frame_number
                    """.formatted(SUMMARY.projection("s"), DETAIL.projection("d")))) {
                while (rows.next()) {
                    byte[] bytes = rows.getBytes("frame_bytes");
                    if (rows.getBigDecimal("account_id") == null) {
                        output.write(bytes);
                        continue;
                    }
                    boolean root = rows.getBigDecimal("auth_date_inv") == null;
                    if (rows.getObject(root ? "s_account_id" : "d_auth_date_inv") == null) {
                        throw new IOException("Imported DFSURGU0 frame refers to a missing key");
                    }
                    unload.writeRecord(output, new Segment(root ? PAUTSUM0 : PAUTDTL1,
                            Arrays.copyOf(bytes, bytes.length - 1),
                            root ? SUMMARY.read(rows, "s_") : DETAIL.read(rows, "d_"), bytes[bytes.length - 1]));
                }
            }
        }
    }

    void loadFlat(InputStream roots, InputStream children) throws SQLException, IOException {
        clear();
        try (var root = SUMMARY.insert(db); var child = DETAIL.insert(db)) {
            for (byte[] bytes; (bytes = record(roots, CardDemoLayout.CIPAUSMY.length())) != null;) {
                var fields = summary.decode(bytes);
                SUMMARY.write(root, null, fields);
            }
            for (byte[] bytes; (bytes = record(children, keyLength + CardDemoLayout.CIPAUDTY.length())) != null;) {
                var account = (BigDecimal) key.decode(Arrays.copyOf(bytes, keyLength)).get(ACCOUNT_KEY);
                DETAIL.write(child, account, detail.decode(Arrays.copyOfRange(bytes, keyLength, bytes.length)));
            }
        }
    }

    void exportFlat(OutputStream roots, OutputStream children, boolean gsam) throws SQLException, IOException {
        try (var query = db.createStatement()) {
            query.setFetchSize(256);
            try (var rows = query.executeQuery("SELECT " + SUMMARY.projection("s")
                    + " FROM pending_auth_summary s ORDER BY s.account_id")) {
                while (rows.next()) roots.write(summary.encode(SUMMARY.read(rows, "s_")));
            }
            try (var rows = query.executeQuery("SELECT d.account_id, " + DETAIL.projection("d")
                    + " FROM pending_auth_detail d ORDER BY d.account_id, d.auth_date_inv, d.auth_time_inv")) {
                while (rows.next()) {
                    if (!gsam) children.write(key.encode(Map.of(ACCOUNT_KEY, rows.getBigDecimal("account_id"))));
                    children.write(detail.encode(DETAIL.read(rows, "d_")));
                }
            }
        }
    }

    private void clear() throws SQLException {
        try (var statement = db.createStatement()) {
            statement.execute("TRUNCATE pending_auth_unload_frame, pending_auth_detail, pending_auth_summary");
        }
    }

    private byte[] encodeFrame(Dfsurgu0Unload.Frame frame) throws IOException {
        var out = new ByteArrayOutputStream();
        unload.writeRecord(out, frame);
        return out.toByteArray();
    }

    private static byte[] record(InputStream input, int length) throws IOException {
        byte[] bytes = input.readNBytes(length);
        if (bytes.length == 0) return null;
        if (bytes.length != length) throw new EOFException("Truncated fixed record");
        return bytes;
    }
}
