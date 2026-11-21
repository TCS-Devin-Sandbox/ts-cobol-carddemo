package com.carddemo.reports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.stereotype.Component;

@Component
class ReportLauncher {
    private final String java;
    private final Path jar;
    private final Path output;
    private final JdbcConnectionDetails database;
    private Process active;

    ReportLauncher(@Value("${carddemo.reports.java:java}") String java,
                   @Value("${carddemo.reports.posting-jar:../java/posting/target/carddemo-posting.jar}") String jar,
                   @Value("${carddemo.reports.output:../work/reports}") String output,
                   JdbcConnectionDetails database) {
        this.java = java;
        this.jar = Path.of(jar);
        this.output = Path.of(output);
        this.database = database;
    }

    synchronized String submit(LocalDate start, LocalDate end) throws IOException {
        if (active != null && active.isAlive()) throw new IOException("TRANREPT already running");
        if (!Files.isRegularFile(jar) || !Files.isReadable(jar)) throw new IOException("Posting jar is not readable");

        String id = UUID.randomUUID().toString();
        Files.createDirectories(output);
        Path run = output.resolve(id);
        if (Files.getFileStore(output).supportsFileAttributeView("posix")) {
            Files.createDirectory(run, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectory(run);
        }
        var launch = new ProcessBuilder(java, "-jar", jar.toAbsolutePath().toString(),
                "--chain", "tranrept", "--start-date", start.toString(), "--end-date", end.toString(),
                "--out", run.toAbsolutePath().toString())
                .redirectErrorStream(true).redirectOutput(run.resolve("launcher.log").toFile());
        var env = launch.environment();
        env.put("POSTING_JDBC_URL", database.getJdbcUrl());
        env.put("PGUSER", database.getUsername());
        env.put("PGPASSWORD", database.getPassword());
        active = launch.start();
        return id;
    }
}
