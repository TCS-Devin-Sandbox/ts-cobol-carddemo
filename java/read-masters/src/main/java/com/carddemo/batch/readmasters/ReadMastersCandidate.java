package com.carddemo.batch.readmasters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.carddemo.batch.common.BatchArtifacts;
import com.carddemo.batch.common.ChainRun;
import com.carddemo.batch.common.VsamTransfer;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ReadMastersJobs.class)
public class ReadMastersCandidate {
    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !args[i].startsWith("--")
                    || options.putIfAbsent(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("Expected one value per option");
            }
        }
        if (!"read-masters".equals(options.get("--chain"))) {
            throw new IllegalArgumentException("The candidate only supports read-masters");
        }
        String url = System.getenv().getOrDefault("READ_MASTERS_JDBC_URL", "jdbc:postgresql://localhost:5432/carddemo");
        run(Path.of(required(options, "--input")), Path.of(required(options, "--out")), url,
                System.getenv().getOrDefault("PGUSER", "carddemo"),
                System.getenv().getOrDefault("PGPASSWORD", "carddemo"), options.get("--run-id"));
    }

    static void run(Path input, Path out, String url, String user, String password) throws Exception {
        run(input, out, url, user, password, null);
    }

    private static void run(Path input, Path out, String url, String user, String password, String runId)
            throws Exception {
        var builder = ChainRun.parameters(input, out);
        for (var master : ReadMaster.values()) {
            builder.addString("missing." + master.job, Boolean.toString(Files.notExists(masterFile(input, master))));
        }
        var parameters = builder.toJobParameters();
        ChainRun.run(ReadMastersCandidate.class, "read_masters", runId, url, user, password, parameters,
                connection -> {
                    var transfer = new VsamTransfer(connection);
                    for (var master : ReadMaster.values()) {
                        if (!"true".equals(parameters.getString("missing." + master.job))) {
                            transfer.load(master.dataset, masterFile(input, master));
                        }
                    }
                }, (dataSource, repository, execution) -> {
                    var codes = new TreeMap<String, Map<String, Integer>>();
                    for (var master : ReadMaster.values()) {
                        var step = repository.getLastStepExecution(execution.getJobInstance(), master.job + ".STEP05");
                        int rc = step.getExecutionContext().getInt("returnCode", 0);
                        codes.put(master.job, master == ReadMaster.ACCOUNT ? Map.of("PREDEL", 0, "STEP05", rc)
                                : Map.of("STEP05", rc));
                    }
                    BatchArtifacts.writeReturnCodes(out, codes);
                });
    }

    private static Path masterFile(Path input, ReadMaster master) {
        return input.resolve("AWS.M2.CARDDEMO." + master.dataset.inputName + ".PS.txt");
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}
