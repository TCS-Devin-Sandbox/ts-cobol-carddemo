package com.carddemo.batch.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.springframework.boot.json.JsonParserFactory;

/** Transport proof only; deliberately performs no posting, interest, or reporting. */
public final class NoOpCandidate {
    private NoOpCandidate() { }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        if (args.length != 10) throw new IllegalArgumentException("Expected --chain --case --input --out --clock");
        for (int i = 0; i < args.length; i += 2) options.put(args[i], args[i + 1]);
        for (String required : List.of("--chain", "--case", "--input", "--out", "--clock")) {
            if (!options.containsKey(required)) throw new IllegalArgumentException("Missing " + required);
        }
        String chain = options.get("--chain");
        if (!List.of("interest", "posting").contains(chain)) throw new IllegalArgumentException("Unsupported chain: " + chain);
        LocalDateTime.parse(options.get("--clock"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SS"));
        Path root = Path.of("").toAbsolutePath();
        var chains = JsonParserFactory.getJsonParser().parseMap(Files.readString(root.resolve("tools/runjcl/chains.json")));
        var spec = (Map<String, Object>) chains.get(chain);
        Path input = Path.of(options.get("--input"));
        Path out = Path.of(options.get("--out"));
        String url = System.getenv().getOrDefault("B4_JDBC_URL", "jdbc:postgresql://localhost:5432/carddemo");
        String user = System.getenv().getOrDefault("PGUSER", "carddemo");
        String password = System.getenv().getOrDefault("PGPASSWORD", "carddemo");
        String schema = "b4_candidate";
        try (var connection = DriverManager.getConnection(url, user, password)) {
            try (var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                sql.execute("CREATE SCHEMA " + schema);
            }
            Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).load().migrate();
            connection.setSchema(schema);
            connection.setAutoCommit(false);
            var transfer = new VsamTransfer(connection);
            for (String dsn : ((Map<String, Object>) spec.get("inputs")).keySet()) {
                var dataset = VsamDataset.forInput(dsn);
                if (dataset == null || dsn.endsWith(".DALYTRAN.PS")) continue;
                transfer.load(dataset, input.resolve(dsn + ".txt"));
            }
            for (var entry : ((Map<String, String>) spec.get("outputs")).entrySet()) {
                Path target = out.resolve("datasets").resolve(entry.getKey().replace("(0)", ".G0001V00") + ".txt");
                var dataset = java.util.Arrays.stream(VsamDataset.values())
                        .filter(value -> value.layout.name().equals(entry.getValue())).findFirst();
                if (dataset.isPresent() && entry.getKey().contains(".VSAM.KSDS")) {
                    transfer.export(dataset.get(), target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.write(target, new byte[0]);
                }
            }
            connection.commit();
        }
        var jobs = new LinkedHashMap<String, Map<String, Integer>>();
        if (chain.equals("interest")) {
            jobs.put("INTCALC", zeros("STEP05 STEP10 STEP15 STEP20 STEP25 STEP30"));
        } else {
            jobs.put("POSTTRAN", zeros("CLCIFIL OPCIFIL STEP05 STEP10 STEP15 STEP20 STEP25 STEP30"));
            jobs.put("TRANREPT", zeros("PRC001 STEP05R STEP10R"));
        }
        BatchArtifacts.writeReturnCodes(out, jobs);
        for (String selector : (List<String>) spec.get("sysout")) {
            String[] parts = selector.split("/");
            BatchArtifacts.writeSysout(out, parts[0], parts[1], "");
        }
    }

    private static Map<String, Integer> zeros(String names) {
        var steps = new LinkedHashMap<String, Integer>();
        for (String name : names.split(" ")) steps.put(name, 0);
        return steps;
    }
}
