package com.carddemo.batch.common;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.RecordLayout;

/** One case/session: retain source representation for unchanged fields, including negative zero. */
public final class VsamTransfer {
    private record Source(byte[] padded, int width, boolean newline) { }
    private final Connection connection;
    private final Map<VsamDataset, Map<List<Object>, Source>> sources = new HashMap<>();

    public VsamTransfer(Connection connection) {
        this.connection = connection;
    }

    public void load(VsamDataset dataset, Path input) throws Exception {
        if (connection.getAutoCommit()) throw new IllegalStateException("Loading requires an explicit transaction");
        var layout = dataset.layout.load();
        var codec = new FixedRecordCodec(layout);
        var provenance = new HashMap<List<Object>, Source>();
        byte[] file = Files.readAllBytes(input);
        var savepoint = connection.setSavepoint();
        try (var delete = connection.createStatement();
                var insert = connection.prepareStatement("INSERT INTO \"" + dataset.table + "\" ("
                        + String.join(",", dataset.columns) + ") VALUES ("
                        + String.join(",", java.util.Collections.nCopies(dataset.columns.length, "?")) + ")")) {
            delete.executeUpdate("DELETE FROM \"" + dataset.table + "\"");
            int start = 0;
            for (int end = 0; end <= file.length; end++) {
                if (end < file.length && file[end] != '\n') continue;
                if (end == file.length && start == end) break;
                int width = end - start;
                if (width > layout.length()) throw new IllegalArgumentException("Record too long: " + input);
                byte[] padded = new byte[layout.length()];
                Arrays.fill(padded, (byte) ' ');
                System.arraycopy(file, start, padded, 0, width);
                var values = codec.decode(padded);
                int i = 1;
                for (var value : values.values()) insert.setObject(i++, value);
                insert.executeUpdate();
                provenance.put(key(values, dataset.keyFields), new Source(padded, width, end < file.length));
                start = end + 1;
            }
            sources.put(dataset, provenance);
            connection.releaseSavepoint(savepoint);
        } catch (Exception failure) {
            connection.rollback(savepoint);
            throw failure;
        }
    }

    public void export(VsamDataset dataset, Path output) throws Exception {
        var layout = dataset.layout.load();
        var codec = new FixedRecordCodec(layout);
        var file = new ByteArrayOutputStream();
        var keys = new java.util.ArrayList<String>();
        for (int i = 0; i < dataset.keyFields; i++) {
            keys.add(dataset.columns[i] + (layout.fields().get(i).kind() == RecordLayout.Kind.TEXT
                    ? " COLLATE \"C\"" : ""));
        }
        String order = String.join(",", keys);
        boolean needsSeparator = false;
        try (var query = connection.createStatement();
                var rows = query.executeQuery("SELECT " + String.join(",", dataset.columns)
                        + " FROM \"" + dataset.table + "\" ORDER BY " + order)) {
            while (rows.next()) {
                if (needsSeparator) file.write('\n');
                var values = new LinkedHashMap<String, Object>();
                for (int i = 0; i < layout.fields().size(); i++) {
                    var field = layout.fields().get(i);
                    values.put(field.name(), field.kind() == RecordLayout.Kind.TEXT
                            ? rows.getString(i + 1) : rows.getBigDecimal(i + 1));
                }
                byte[] encoded = codec.encode(values);
                var source = sources.getOrDefault(dataset, Map.of()).get(key(values, dataset.keyFields));
                int width = encoded.length;
                boolean newline = true;
                if (source != null) {
                    var original = codec.decode(source.padded());
                    int offset = 0;
                    for (var field : layout.fields()) {
                        if (original.get(field.name()).equals(values.get(field.name()))) {
                            System.arraycopy(source.padded(), offset, encoded, offset, field.length());
                        }
                        offset += field.length();
                    }
                    if (Arrays.equals(Arrays.copyOfRange(encoded, source.width(), encoded.length),
                            Arrays.copyOfRange(source.padded(), source.width(), encoded.length))) width = source.width();
                    newline = source.newline();
                }
                file.write(encoded, 0, width);
                if (newline) file.write('\n');
                needsSeparator = !newline;
            }
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, file.toByteArray());
    }

    private static List<Object> key(Map<String, Object> values, int count) {
        return values.values().stream().limit(count).toList();
    }
}
