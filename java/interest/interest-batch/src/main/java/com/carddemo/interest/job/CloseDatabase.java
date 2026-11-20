package com.carddemo.interest.job;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Frozen V001/V002, isolated from the pilot's pre-existing interest_batch schema. */
public final class CloseDatabase {
    private CloseDatabase() {}

    public static DataSource initialize(String serverUrl, String user, String password, String database) throws Exception {
        if (!database.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid private database name");
        int slash = serverUrl.lastIndexOf('/');
        int query = serverUrl.indexOf('?', slash);
        String target = serverUrl.substring(0, slash + 1) + database + (query < 0 ? "" : serverUrl.substring(query));
        try (Connection c = DriverManager.getConnection(serverUrl, user, password); Statement s = c.createStatement()) {
            s.execute("SELECT pg_advisory_lock(4904001)");
            try (var p = c.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
                p.setString(1, database);
                try (ResultSet rs = p.executeQuery()) {
                    if (!rs.next()) s.execute("CREATE DATABASE " + database);
                }
            }
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(target, user, password);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("SELECT pg_advisory_xact_lock(4904002)");
            try (ResultSet rs = s.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname='interest'")) {
                if (rs.next()) { c.commit(); return ds; }
            }
            for (String resource : new String[]{"db/migration/V001__interest.sql", "db/migration/V002__batch_repository.sql"}) {
                String sql = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8).replaceAll("(?m)--[^\\r\\n]*", "");
                for (String part : sql.split(";")) {
                    String statement = part.strip();
                    if (statement.isEmpty()) continue;
                    if (statement.startsWith("CREATE ROLE ")) {
                        String role = statement.split("\\s+")[2];
                        try (var p = c.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")) {
                            p.setString(1, role);
                            try (ResultSet rs = p.executeQuery()) { if (rs.next()) continue; }
                        }
                    }
                    var savepoint = c.setSavepoint();
                    try {
                        s.execute(statement);
                    } catch (SQLException e) {
                        c.rollback(savepoint);
                        if (!statement.startsWith("CREATE ROLE ") || !"42710".equals(e.getSQLState())) throw e;
                    } finally {
                        c.releaseSavepoint(savepoint);
                    }
                }
            }
            c.commit();
        }
        return ds;
    }
}
