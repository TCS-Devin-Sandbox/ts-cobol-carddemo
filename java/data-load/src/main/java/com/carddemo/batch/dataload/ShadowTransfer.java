package com.carddemo.batch.dataload;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import com.carddemo.batch.common.VsamDataset;
import com.carddemo.batch.common.VsamTransfer;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ShadowTransfer {
    private ShadowTransfer() { }

    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[1]), output = Path.of(args[2]);
        Files.createDirectories(output);
        var json = new ObjectMapper();
        if (args[0].equals("encode")) {
            var entries = new ArrayList<DataLoad.Entry>();
            try (var paths = Files.list(input)) {
                for (Path path : paths.sorted().toList()) {
                    if (path.getFileName().toString().equals("AWS.M2.CARDDEMO.DALYTRAN.PS.txt")) continue;
                    var dataset = VsamDataset.forInput(path.getFileName().toString());
                    if (dataset == null) continue;
                    var layout = dataset.layout.load();
                    var latin = new FixedRecordCodec(layout);
                    var ebcdic = new FixedRecordCodec(layout, Charset.forName("IBM037"));
                    try (var stream = Files.newOutputStream(output.resolve(path.getFileName()))) {
                        for (String line : Files.readAllLines(path, StandardCharsets.ISO_8859_1)) {
                            byte[] padded = new byte[layout.length()];
                            Arrays.fill(padded, (byte) ' ');
                            byte[] raw = line.getBytes(StandardCharsets.ISO_8859_1);
                            System.arraycopy(raw, 0, padded, 0, raw.length);
                            stream.write(ebcdic.encode(latin.decode(padded)));
                        }
                    }
                    entries.add(new DataLoad.Entry(path.getFileName().toString(), dataset));
                }
            }
            json.writeValue(output.resolve("manifest.json").toFile(), entries);
        } else if (args[0].equals("export")) {
            try (var connection = DriverManager.getConnection(System.getenv("JDBC_URL"),
                    System.getenv("JDBC_USER"), System.getenv("JDBC_PASSWORD"))) {
                connection.setSchema(System.getenv("JDBC_SCHEMA"));
                var transfer = new VsamTransfer(connection);
                for (var entry : json.readValue(input.resolve("manifest.json").toFile(), DataLoad.Entry[].class)) {
                    Path target = output.resolve(entry.file()).normalize();
                    if (!target.startsWith(output.normalize())) throw new IllegalArgumentException("Unsafe export path");
                    transfer.export(entry.dataset(), target);
                }
            }
        } else {
            throw new IllegalArgumentException("Expected encode or export");
        }
    }
}
