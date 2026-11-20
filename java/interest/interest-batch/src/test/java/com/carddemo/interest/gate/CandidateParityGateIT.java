package com.carddemo.interest.gate;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CandidateParityGateIT extends GateSupport {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"i01-baseline", "i02-tie-half-cent", "i03-zero-rate-skipped", "i04-default-group-fallback",
            "i05-multi-category", "i06-negative-balance", "i07-nontie-rounding"})
    void INT_R01_R10_Q01_Q02_Q03_candidateMatchesGoldenBytesAndDatabase(String name) throws Exception {
        Path output = temp.resolve(name);
        var execution = launch(fixture(name).resolve("input"), output);
        assertEquals("0", execution.getExitStatus().getExitCode(), name + " rc");
        assertTrue(Files.readString(output.resolve("rc.json")).contains("\"maxrc\":0"), name + " rc.json");
        for (var dataset : java.util.Map.of(ACCOUNT, 300, TRAN, 350).entrySet()) {
            var expected = rows(fixture(name).resolve("expected").resolve(dataset.getKey()), dataset.getValue());
            records(name + " file=" + dataset.getKey(), expected, rows(output.resolve(dataset.getKey()), dataset.getValue()));
        }
        String sysout = "sysout/INTCALC.STEP15.txt";
        bytes(name + " file=" + sysout + " record=0", Files.readAllBytes(fixture(name).resolve("expected").resolve(sysout)),
                Files.readAllBytes(output.resolve(sysout)));
        var facts = facts(name);
        var run = runId(execution);
        assertEquals(facts.txCount(), count("interest_tx", run), name + " transaction count");
        assertEquals(facts.deltaCount(), count("account_delta", run), name + " delta count");
        var txSum = jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM interest.interest_tx WHERE run_id=?", BigDecimal.class, run);
        var deltaSum = jdbc.queryForObject("SELECT coalesce(sum(new_current_balance-old_current_balance),0) FROM interest.account_delta WHERE run_id=?",
                BigDecimal.class, run);
        assertEquals(0, facts.txSum().compareTo(txSum), name + " signed tx sum");
        assertEquals(0, facts.deltaSum().compareTo(deltaSum), name + " signed delta sum");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM interest.account_delta WHERE run_id=? AND acct_id=?",
                Integer.class, run, facts.lastAccount()), name + " INT-Q02 last account delta");
        records(name + " file=PG.interest_tx", rows(fixture(name).resolve("expected").resolve(TRAN), 350),
                jdbc.query("SELECT raw_record FROM interest.interest_tx WHERE run_id=? ORDER BY emit_ordinal",
                        (rs, n) -> rs.getBytes(1), run));
        assertEquals(1, count("publication", run), name + " publication count");
        assertEquals("PRIVATE", jdbc.queryForObject("SELECT state FROM interest.publication WHERE run_id=?", String.class, run), name);
        assertEquals("COMPUTED", state(run), name);
        invariants();
        System.out.printf("GATE case=%s tx=%d deltas=%d tx_sum=%s delta_sum=%s bytes=identical%n",
                name, facts.txCount(), facts.deltaCount(), txSum, deltaSum);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"missing-default-rate", "missing-account", "missing-xref", "malformed-tcatbalf", "missing-input"})
    void INT_R10_failuresHaveNoPublishedEffects(String name) throws Exception {
        Path output = temp.resolve(name);
        var execution = launch(specimen(name), output);
        var run = runId(execution);
        assertEquals("12", execution.getExitStatus().getExitCode(), name + " rc");
        assertTrue(Files.readString(output.resolve("rc.json")).contains("\"maxrc\":12"), name);
        assertFalse(Files.exists(output.resolve(TRAN)), name + " no SYSTRAN");
        assertFalse(Files.exists(output.resolve(ACCOUNT)), name + " no ACCTDATA");
        assertEquals(0, count("publication", run), name + " no publication");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM interest.run WHERE run_id=? AND state IN ('COMPUTED','RELEASED')",
                Integer.class, run), name + " no completed/released run");
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM interest.account_delta d JOIN interest.publication p USING(run_id)
                WHERE d.run_id=? AND p.state='RECEIVED'""", Integer.class, run), name + " no published delta");
        invariants();
        System.out.println("GATE failure=" + name + " rc=12 publications=0 published_deltas=0");
    }
}
