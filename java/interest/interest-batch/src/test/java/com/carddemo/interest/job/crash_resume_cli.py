"""Disposable PG15 process-crash rehearsal; run from the repository root after verify."""
import json
from pathlib import Path
import subprocess

ROOT = Path.cwd()
JAR = "java/interest/interest-batch/target/interest-batch-0.1.0-PILOT.jar"
MAIN = "com.carddemo.interest.job.InterestCloseApplication"
DATABASE = "interest_crash_review"


def run(command, expected=0):
    print("$ " + " ".join(command), flush=True)
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(result.stdout, end="", flush=True)
    print(f"RC={result.returncode}", flush=True)
    assert result.returncode == expected, result.stdout
    return result.stdout


def sql(query):
    return run(["docker", "compose", "exec", "-T", "db", "psql", "-XAt", "-U", "carddemo",
                "-d", DATABASE, "-v", "ON_ERROR_STOP=1", "-c", query]).strip()


def image(run_id):
    return sql(f"""SELECT json_build_object(
        'run',(SELECT row_to_json(r) FROM interest.run r WHERE run_id='{run_id}'),
        'checkpoint',(SELECT row_to_json(c) FROM interest.run_checkpoint c WHERE run_id='{run_id}'),
        'transactions',(SELECT coalesce(json_agg(t ORDER BY emit_ordinal),'[]') FROM interest.interest_tx t WHERE run_id='{run_id}'),
        'deltas',(SELECT coalesce(json_agg(d ORDER BY acct_id),'[]') FROM interest.account_delta d WHERE run_id='{run_id}'))""")


def totals(run_id):
    sql(f"SELECT count(*),coalesce(sum(amount),0) FROM interest.interest_tx WHERE run_id='{run_id}'; "
        f"SELECT count(*),coalesce(sum(new_current_balance),0) FROM interest.account_delta WHERE run_id='{run_id}'; "
        f"SELECT * FROM interest.run_checkpoint WHERE run_id='{run_id}'")


for phase in ("before", "after"):
    print(f"\n=== Runtime.halt {phase} checkpoint group commit ===", flush=True)
    output = f"work/04-calculation-restart/crash-cli-{phase}"
    base = ["docker", "compose", "exec", "-T", "estate", "java"]
    common = ["--interest.close.run-on-startup=true", "--interest.close.jdbc-url=jdbc:postgresql://db:5432/carddemo",
              f"--interest.close.database={DATABASE}", "--interest.close.input-dir=fixtures/interest/i01-baseline/input",
              f"--interest.close.output-dir={output}"]
    crash = base + [f"-Dcrash.phase={phase}", "-Dloader.path=/estate/java/interest/interest-batch/target/test-classes",
                    "-Dloader.main=com.carddemo.interest.job.CrashCloseProcess", "-cp", JAR,
                    "org.springframework.boot.loader.launch.PropertiesLauncher"] + common
    log = run(crash, 77)
    marker = next(line for line in log.splitlines() if line.startswith("CRASH run="))
    run_id = marker.split()[1].split("=")[1]
    before = json.loads(image(run_id))
    assert before["run"]["state"] == "COMPUTING"
    assert len(before["transactions"]) == (0 if phase == "before" else 1)
    totals(run_id)
    cli = base + [f"-Dloader.main={MAIN}", "-cp", JAR,
                  "org.springframework.boot.loader.launch.PropertiesLauncher"] + common + [f"--interest.close.resume-run-id={run_id}"]
    run(cli, 12)
    assert before == json.loads(image(run_id)), "refused CLI changed persisted state"
    run(cli + ["--interest.close.abandoned=true"])
    after = json.loads(image(run_id))
    assert after["run"]["state"] == "COMPUTED"
    assert before["run"]["snapshot_id"] == after["run"]["snapshot_id"]
    for key in ("transactions", "deltas"):
        assert all(row in after[key] for row in before[key]), f"committed {key} changed"
    totals(run_id)
    for artifact in ("datasets/AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.txt",
                     "datasets/AWS.M2.CARDDEMO.SYSTRAN.G0001V00.txt", "sysout/INTCALC.STEP15.txt"):
        assert (ROOT / output / artifact).read_bytes() == (ROOT / "fixtures/interest/i01-baseline/expected" / artifact).read_bytes(), artifact
    print(f"PASS {phase}: halt RC=77; unconfirmed RC=12 state untouched; abandoned RC=0; same snapshot; committed rows byte-identical; golden identical", flush=True)
