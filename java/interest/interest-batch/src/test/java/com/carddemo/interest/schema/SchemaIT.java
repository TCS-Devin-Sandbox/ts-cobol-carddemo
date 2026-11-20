package com.carddemo.interest.schema;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

class SchemaIT {
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    static final String SNAP = "00000000-0000-0000-0000-000000000001";
    static final String RUN = "00000000-0000-0000-0000-000000000002";
    static Connection db;
    static {
        PG.start();
    }

    @BeforeAll static void migrate() throws Exception {
        db = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        execute("CREATE SCHEMA carddemo");
        execute("CREATE TABLE carddemo.transaction_type(id TEXT PRIMARY KEY)");
        execute("CREATE TABLE carddemo.transaction_type_categor(id TEXT PRIMARY KEY)");
        execute("INSERT INTO carddemo.transaction_type VALUES ('01')");
        execute("INSERT INTO carddemo.transaction_type_categor VALUES ('0005')");
        String before = reference();
        for (String file : List.of("V001__interest.sql", "V002__batch_repository.sql")) {
            ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/" + file));
            System.out.println("MIGRATION " + file + " applied");
        }
        assertEquals(before, reference());
        System.out.println("TN-08 carddemo tables, contents and ACL unchanged: " + before);
        System.out.println("DB " + scalar("SELECT version()"));
        execute("INSERT INTO interest.snapshot VALUES ('" + SNAP + "', '{}', 'lab-ascii', 'SEALED', '2022-07-05 10:00:00')");
        execute("INSERT INTO interest.run VALUES ('" + RUN + "', '" + SNAP
                + "', '2022071800', '202207', 'SYSTRAN', 'shadow', 'INT-02', 'legacy-A', 'frozen', 'READY')");
    }

    static String reference() throws Exception {
        return scalar("SELECT string_agg(c.relname || ':' || coalesce(c.relacl::text,'NULL'), ',' ORDER BY c.relname) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='carddemo'")
                + scalar("SELECT string_agg(id, ',') FROM carddemo.transaction_type")
                + scalar("SELECT string_agg(id, ',') FROM carddemo.transaction_type_categor");
    }

    static void execute(String sql) throws SQLException {
        try (Statement s = db.createStatement()) { s.execute(sql); }
    }
    static String scalar(String sql) throws SQLException {
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertTrue(r.next()); return r.getString(1);
        }
    }
    static void rejected(String sql, String state) {
        SQLException e = assertThrows(SQLException.class, () -> execute(sql));
        assertEquals(state, e.getSQLState(), sql);
        System.out.println("REJECT " + state + " " + e.getMessage().split("\\n")[0]);
    }

    static LinkedHashMap<String,String> row(String table) throws Exception {
        LinkedHashMap<String,String> values = new LinkedHashMap<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery(
                "SELECT column_name,data_type,character_maximum_length FROM information_schema.columns WHERE table_schema='interest' AND table_name='" + table + "' ORDER BY ordinal_position")) {
            while(r.next()) {
                String name = r.getString(1), type = r.getString(2);
                String value = switch(type) {
                    case "uuid" -> "'" + (name.equals("snapshot_id") ? SNAP : RUN) + "'";
                    case "character varying" -> "'" + "0".repeat(r.getInt(3)) + "'";
                    case "numeric" -> "12.00";
                    case "bigint" -> "1";
                    case "boolean" -> "true";
                    case "bytea" -> "decode(repeat('20'," + switch(name) {
                        case "legacy_key" -> table.equals("disclosure_rate_input") ? 16 : 17;
                        case "raw_record" -> table.equals("account_input") ? 300 : table.equals("interest_tx") ? 350 : 50;
                        case "source_fingerprint" -> 32;
                        default -> switch(table) {
                            case "account_input" -> 178; case "card_xref_input" -> 14;
                            case "category_balance_input" -> 22; case "disclosure_rate_input" -> 28; default -> 20;
                        };
                    } + "),'hex')";
                    default -> "'synthetic-i01-evidence'";
                };
                values.put(name,value);
            }
        }
        if(values.containsKey("acct_id")) values.put("acct_id", "'00000002001'");
        if(values.containsKey("txn_id")) values.put("txn_id", "'2022071800000001'");
        if(values.containsKey("card_number")) values.put("card_number", "'4000000000002001'");
        return values;
    }
    static String insert(String table, Map<String,String> values) {
        return "INSERT INTO interest." + table + "(" + String.join(",",values.keySet())
                + ") VALUES (" + String.join(",",values.values()) + ")";
    }

    @Test void INT_MAP_allLayoutsWidthsNullsKeysAndTypmods() throws Exception {
        Map<String,Integer> widths = Map.ofEntries(
                Map.entry("acct_id",11), Map.entry("active_status",1), Map.entry("open_date",10),
                Map.entry("expiration_date",10), Map.entry("reissue_date",10), Map.entry("addr_zip",10),
                Map.entry("group_id",10), Map.entry("card_number",16), Map.entry("customer_id",9),
                Map.entry("txn_type",2), Map.entry("category_code",4), Map.entry("txn_id",16),
                Map.entry("source",10), Map.entry("description",100), Map.entry("merchant_id",9),
                Map.entry("merchant_name",50), Map.entry("merchant_city",50), Map.entry("merchant_zip",10),
                Map.entry("orig_ts",26), Map.entry("proc_ts",26));
        for(String table : List.of("account_input","card_xref_input","category_balance_input",
                "disclosure_rate_input","interest_tx","account_delta")) {
            var values = row(table);
            execute("BEGIN");
            execute(insert(table,values));
            rejected(insert(table,values),"23505");
            execute("ROLLBACK");
            for(String column : values.keySet()) {
                var bad = new LinkedHashMap<>(values);
                bad.put(column,"NULL");
                rejected(insert(table,bad),"23502");
            }
            try(Statement s=db.createStatement(); ResultSet r=s.executeQuery(
                    "SELECT column_name,data_type,character_maximum_length,numeric_precision,numeric_scale FROM information_schema.columns WHERE table_schema='interest' AND table_name='"+table+"'")) {
                while(r.next()) {
                    String name=r.getString(1),type=r.getString(2);
                    if(type.equals("character varying")) assertEquals(widths.get(name).intValue(),r.getInt(3));
                    if(type.equals("numeric")) {
                        int precision = name.equals("balance")||name.equals("amount")?11:name.equals("annual_percent_rate")?6:12;
                        assertEquals(precision,r.getInt(4)); assertEquals(2,r.getInt(5));
                    }
                    if(type.equals("bytea")||type.equals("character varying")) {
                        var bad=new LinkedHashMap<>(values);
                        bad.put(name,type.equals("bytea")?"decode('00','hex')":"''");
                        rejected(insert(table,bad),"23514");
                    }
                    System.out.println("METADATA "+table+"."+name+" "+type+"("+r.getString(3)+","+r.getString(4)+","+r.getString(5)+")");
                }
            }
            System.out.println("PASS INT-MAP "+table+" valid i01-shaped row / nulls / widths / duplicate PK");
        }
    }

    @Test void INT_H03_nonuniqueAccountOneFlaggedAixFirstAndMissingLayouts() throws Exception {
        var a=row("card_xref_input");
        execute(insert("card_xref_input",a));
        a.put("card_number","'4000000000002002'"); a.put("aix_first","false");
        execute(insert("card_xref_input",a));
        rejected("UPDATE interest.card_xref_input SET aix_first=true WHERE card_number='4000000000002002'","23505");
        assertEquals("0",scalar("SELECT count(*) FROM interest.account_input"));
        execute("DELETE FROM interest.card_xref_input");
        System.out.println("PASS INT-H03 same-account xrefs; sole flagged first; INT-R10 absent account/rate permitted");
    }

    @Test void INT_H05_ordinalsNotTransactionIdsAreUnique() throws Exception {
        var a=row("interest_tx");
        execute(insert("interest_tx",a));
        a.put("emit_ordinal","2"); execute(insert("interest_tx",a));
        rejected(insert("interest_tx",a),"23505");
        a.put("emit_ordinal","0"); rejected(insert("interest_tx",a),"23514");
        String other="00000000-0000-0000-0000-000000000003";
        execute("INSERT INTO interest.run SELECT '"+other+"',snapshot_id,parm,cycle,feed,mode,build_revision,rule_revision,clock_profile,state FROM interest.run WHERE run_id='"+RUN+"'");
        a.put("run_id","'"+other+"'"); a.put("emit_ordinal","1"); execute(insert("interest_tx",a));
        execute("DELETE FROM interest.interest_tx");
        execute("DELETE FROM interest.run WHERE run_id='"+other+"'");
        System.out.println("PASS INT-H05 duplicate TRAN-ID retained within/across runs; positive per-run ordinal PK");
    }

    @Test void INT_R09_byteaIndexAndUnsignedOrdering() throws Exception {
        assertTrue(scalar("SELECT indexdef FROM pg_indexes WHERE schemaname='interest' AND tablename='category_balance_input' AND indexdef LIKE '%(snapshot_id, legacy_key)%'").contains("UNIQUE"));
        assertEquals("00,7f,80,ff",scalar("SELECT string_agg(encode(k,'hex'),',' ORDER BY k) FROM (VALUES (decode('ff','hex')),(decode('80','hex')),(decode('00','hex')),(decode('7f','hex'))) v(k)"));
        System.out.println("PASS INT-R09 unique snapshot/BYTEA btree; unsigned order 00,7f,80,ff");
        for(String table:List.of("category_balance_input","disclosure_rate_input")) {
            var a=row(table);
            execute(insert(table,a));
            a.put("category_code","'0001'");
            rejected(insert(table,a),"23505");
            execute("DELETE FROM interest."+table);
        }
        System.out.println("PASS INT-MAP duplicate encoded keys rejected independently of decoded PK");
    }

    @Test void A1_schemaGrantsDefaultsCascadesAndInventory() throws Exception {
        assertEquals("10",scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema='interest'"));
        assertEquals("6",scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema='interest_batch'"));
        assertEquals("account_delta,account_input,card_xref_input,category_balance_input,disclosure_rate_input,interest_tx,publication,run,run_checkpoint,snapshot",
                scalar("SELECT string_agg(table_name,',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema='interest'"));
        assertEquals("batch_job_execution,batch_job_execution_context,batch_job_execution_params,batch_job_instance,batch_step_execution,batch_step_execution_context",
                scalar("SELECT string_agg(table_name,',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema='interest_batch'"));
        assertEquals("3",scalar("SELECT count(*) FROM information_schema.sequences WHERE sequence_schema='interest_batch'"));
        assertEquals("0",scalar("SELECT count(*) FROM information_schema.columns WHERE table_schema IN ('interest','interest_batch') AND column_default IS NOT NULL"));
        assertEquals("0",scalar("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname IN ('interest','interest_batch') AND c.contype='f' AND c.confdeltype <> 'a'"));
        for(String role:List.of("interest_intake","interest_compute","interest_publish")) {
            assertEquals("f",scalar("SELECT has_schema_privilege('"+role+"','interest_batch','USAGE')"));
        }
        assertEquals("f",scalar("SELECT has_schema_privilege('interest_repository','interest','USAGE')"));
        assertEquals("t",scalar("SELECT has_table_privilege('interest_intake','interest.account_input','INSERT')"));
        assertEquals("f",scalar("SELECT has_table_privilege('interest_compute','interest.account_input','UPDATE')"));
        assertEquals("f",scalar("SELECT has_table_privilege('interest_publish','interest.interest_tx','INSERT')"));
        assertEquals("t",scalar("SELECT has_table_privilege('interest_repository','interest_batch.batch_job_execution','INSERT')"));
        System.out.println("PASS A1 ten business/six repository tables; distinct grants; no defaults/cascades");
    }

    @Test void A3_emptySnapshotCheckpointPublicationAndMetadataForeignKeys() throws Exception {
        assertEquals("0",scalar("SELECT count(*) FROM interest.category_balance_input"));
        execute("INSERT INTO interest.run_checkpoint VALUES ('"+RUN+"',NULL,'00000002001',0)");
        rejected("UPDATE interest.run_checkpoint SET next_legacy_key=decode('00','hex')","23514");
        rejected("UPDATE interest.run_checkpoint SET last_ordinal=-1","23514");
        rejected("DELETE FROM interest.run WHERE run_id='"+RUN+"'","23503");
        execute("INSERT INTO interest.publication VALUES ('"+RUN+"','PRIVATE',decode(repeat('00',32),'hex'),0,'shadow','G0001V00','G0002V00','{}','synthetic-op',NULL)");
        rejected("UPDATE interest.publication SET payload_checksum=decode('00','hex')","23514");
        rejected("UPDATE interest.publication SET record_count=-1","23514");
        rejected("INSERT INTO interest.account_input(snapshot_id) VALUES ('"+SNAP+"')","23502");
        execute("DELETE FROM interest.publication");
        execute("DELETE FROM interest.run_checkpoint");
        System.out.println("PASS A3 empty snapshot and zero-output private publication; checkpoint widths; restrictive metadata FK");
    }

    @AfterAll static void close() throws Exception { db.close(); }
}
