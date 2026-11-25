package com.carddemo.batch.authdata;

import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

public final class AuthData {
    private AuthData() {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv()));
    }

    static int run(String[] args, Map<String, String> env) {
        if (args.length < 2 || args.length != switch (args[0]) {
            case "load-unload", "export-unload" -> 2;
            case "load-flat", "export-flat", "export-gsam" -> 3;
            default -> 0;
        }) {
            System.err.println("Usage: auth-data {load-unload|export-unload} UNLOAD"
                    + " or {load-flat|export-flat|export-gsam} ROOT CHILD");
            return 2;
        }
        try {
            String url = required(env, "JDBC_URL");
            String user = required(env, "JDBC_USER");
            String password = required(env, "JDBC_PASSWORD");
            String schema = env.getOrDefault("JDBC_SCHEMA", "public");
            Flyway.configure().dataSource(url, user, password).schemas(schema).load().migrate();
            try (var db = DriverManager.getConnection(url, user, password)) {
                db.setSchema(schema);
                db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                db.setAutoCommit(false);
                try {
                    execute(new AuthTransfer(db), args);
                    db.commit();
                } catch (Exception failure) {
                    db.rollback();
                    throw failure;
                }
            }
            return 0;
        } catch (Exception failure) {
            String detail = failure instanceof SQLException sql ? "SQLSTATE " + sql.getSQLState()
                    : failure instanceof IOException || failure instanceof IllegalArgumentException
                    ? failure.getMessage() : failure.getClass().getSimpleName();
            System.err.println("Authorization transfer failed: " + detail);
            return 2;
        }
    }

    private static void execute(AuthTransfer tool, String[] args) throws SQLException, IOException {
        Path first = Path.of(args[1]);
        switch (args[0]) {
            case "load-unload" -> tool.loadUnload(first);
            case "export-unload" -> {
                try (var out = Files.newOutputStream(first, WRITE, CREATE_NEW)) { tool.exportUnload(out); }
            }
            case "load-flat" -> {
                try (var roots = Files.newInputStream(first); var children = Files.newInputStream(Path.of(args[2]))) {
                    tool.loadFlat(roots, children);
                }
            }
            case "export-flat", "export-gsam" -> {
                try (var roots = Files.newOutputStream(first, WRITE, CREATE_NEW);
                     var children = Files.newOutputStream(Path.of(args[2]), WRITE, CREATE_NEW)) {
                    tool.exportFlat(roots, children, args[0].equals("export-gsam"));
                }
            }
        }
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}
