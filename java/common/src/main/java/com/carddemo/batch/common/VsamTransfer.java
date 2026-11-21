package com.carddemo.batch.common;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.LinkedHashMap;

import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.carddemo.batch.common.codec.RecordLayout;

/** PostgreSQL-only transport using canonical codec values and LF-terminated, space-trimmed fixture lines. */
public final class VsamTransfer {
    @FunctionalInterface
    public interface RecordSource {
        java.util.Map<String, Object> next() throws Exception;
    }

    @FunctionalInterface
    public interface RecordConsumer {
        void accept(java.util.Map<String, Object> record) throws Exception;
    }

    public void loadRecords(VsamDataset dataset, RecordSource source) throws Exception {
        if (connection.getAutoCommit()) throw new IllegalStateException("Loading requires an explicit transaction");
        var fields = dataset.layout.load().fields();
        var savepoint = connection.setSavepoint();
        try (var delete = connection.createStatement();
                var insert = connection.prepareStatement("INSERT INTO \"" + dataset.table + "\" ("
                        + String.join(",", dataset.columns) + ") VALUES ("
                        + String.join(",", java.util.Collections.nCopies(dataset.columns.length, "?")) + ")")) {
            delete.executeUpdate("DELETE FROM \"" + dataset.table + "\"");
            for (var values = source.next(); values != null; values = source.next()) {
                for (int i = 0; i < fields.size(); i++) insert.setObject(i + 1, values.get(fields.get(i).name()));
                insert.executeUpdate();
            }
            connection.releaseSavepoint(savepoint);
        } catch (Exception failure) {
            connection.rollback(savepoint);
            throw failure;
        }
    }

    private final Connection connection;

    public VsamTransfer(Connection connection) {
        this.connection = connection;
    }

    public void stageFromPublic(VsamDataset... datasets) throws Exception {
        String schema = connection.getSchema();
        if (connection.getAutoCommit() || schema == null || "public".equals(schema)) {
            throw new IllegalStateException("Staging requires a transaction in a separate schema");
        }
        // A single SQL statement keeps the copied tables on the same PostgreSQL snapshot.
        var copies = new java.util.StringJoiner(", ", "WITH ", " SELECT 1");
        for (var dataset : datasets) {
            String table = "\"" + dataset.table + "\"";
            String columns = String.join(",", dataset.columns);
            copies.add("stage_" + dataset.table + " AS (INSERT INTO " + table + " (" + columns
                    + ") SELECT " + columns + " FROM public." + table + ")");
        }
        try (var sql = connection.createStatement()) {
            sql.execute(copies.toString());
        }
    }

    public void load(VsamDataset dataset, Path input) throws Exception {
        if (connection.getAutoCommit()) throw new IllegalStateException("Loading requires an explicit transaction");
        var layout = dataset.layout.load();
        var codec = new FixedRecordCodec(layout);
        byte[] file = Files.readAllBytes(input);
        int[] start = {0};
        loadRecords(dataset, () -> {
            if (start[0] == file.length) return null;
            int end = start[0];
            while (end < file.length && file[end] != '\n') end++;
            int width = end - start[0];
            if (width > layout.length()) throw new IllegalArgumentException("Record too long: " + input);
            byte[] padded = new byte[layout.length()];
            Arrays.fill(padded, (byte) ' ');
            System.arraycopy(file, start[0], padded, 0, width);
            start[0] = end < file.length ? end + 1 : end;
            return codec.decode(padded);
        });
    }

    public void export(VsamDataset dataset, Path output) throws Exception {
        var layout = dataset.layout.load();
        var codec = new FixedRecordCodec(layout);
        var file = new ByteArrayOutputStream();
        readRecords(dataset, values -> {
            byte[] encoded = codec.encode(values);
            int width = encoded.length;
            while (width > 0 && encoded[width - 1] == ' ') width--;
            file.write(encoded, 0, width);
            file.write('\n');
        });
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, file.toByteArray());
    }

    public void readRecords(VsamDataset dataset, RecordConsumer consumer) throws Exception {
        var layout = dataset.layout.load();
        var keys = new java.util.ArrayList<String>();
        for (int i = 0; i < dataset.keyFields; i++) {
            keys.add(dataset.columns[i] + (layout.fields().get(i).kind() == RecordLayout.Kind.TEXT
                    ? " COLLATE \"C\"" : ""));
        }
        String order = String.join(",", keys);
        try (var query = connection.prepareStatement("SELECT " + String.join(",", dataset.columns)
                        + " FROM \"" + dataset.table + "\" ORDER BY " + order)) {
            query.setFetchSize(1000);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    var values = new LinkedHashMap<String, Object>();
                    for (int i = 0; i < layout.fields().size(); i++) {
                        var field = layout.fields().get(i);
                        values.put(field.name(), field.kind() == RecordLayout.Kind.TEXT
                                ? rows.getString(i + 1) : rows.getBigDecimal(i + 1));
                    }
                    consumer.accept(values);
                }
            }
        }
    }
}
