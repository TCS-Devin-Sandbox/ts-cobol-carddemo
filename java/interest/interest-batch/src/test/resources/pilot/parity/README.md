# Interest parity driver

Run serially: like `record.run_case`, this deletes/recreates the estate's shared
`datasets/` and the selected `--out` directory (restricted to below `work/`).
It imports the unchanged `Runner`, `materialise_inputs` and `unload` helpers.
All five setup jobs in `chains.json` run on one Runner; its accumulated RC keys
are filtered exactly as the recorder filters them. Only STEP15 is replaced,
using the Java **process exit code**, not the Java-exported rc.json. INTCALC's
MAXRC is that single step's RC, not the maximum of unrelated setup job codes.
A setup RC > 8 or a signalled Java process fails the driver.

STEP15 reads ACCTDATA, CARDXREF (including its account alternate index), TCATBALF
and DISCGRP KSDS files. The driver unloads those **after real setup** via the
recorder helper, preserving primary-key order. It renames the unloaded files to
the pilot's `*.PS.txt` intake convention; it does not feed fixture files directly
to Java. Java builds the account lookup from the same CARDXREF records. The
pilot holds duplicate account matches because host alternate-index ordering is
unproven. Lab inputs are ASCII/zoned, with no embedded newlines.

The Java exporter writes the changed ACCTDATA image, new SYSTRAN G0001V00 and
`sysout/INTCALC.STEP15.txt` from committed Java SYSOUT rows (not application logs).
The driver frames output exactly like `unload`: one record per LF-terminated
line, only trailing spaces stripped, and adds the same layout/lrecl/records
manifest. Names are resolved through Runner; lengths come from its catalog
using the recorder's KSDS recsize / flat lrecl / 80 fallback rules. The driver
uses Runner.allocate on the real STEP15 TRANSACT DD to register the new GDG and
its JCL DCB before Java runs; this does not execute the COBOL step or add RCs.
It does not load Java outputs back into legacy KSDS; this is a
ticket-scoped parity bridge, not a production GDG/DISP adapter. No golden,
comparator or legacy helper is changed. Setup job logs and intake unloads are
retained under each actual directory for provenance.

## Exact commands (repository root)

The estate Dockerfile already installs Java 21 as well as GnuCOBOL. No extra
mount or container package installation is needed. Maven runs on the host;
install `openjdk-21-jdk-headless` there if absent. Docker Hub 429 is avoided
using the existing `ESTATE_BASE_IMAGE` build argument, without tracked edits.

```sh
export ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04
make up && make build && make parity CHAIN=interest
(cd java/interest/interest-batch && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean verify)
P=java/interest/interest-batch/src/test/resources/pilot
python3 -m unittest discover -s "$P/parity" -v
for path in fixtures/interest/*; do
  case=${path##*/}
  docker compose exec -T \
    -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
    -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
    estate python3 "$P/parity/run_case.py" "$case" --out "work/java-pilot/$case" || break
  python3 tools/parity/compare.py interest "$case" \
    --actual "work/java-pilot/$case" --label java --report "$P/evidence/compare-report.md" || break
done
```

Do not run in parallel with `make parity` or another harness run. PostgreSQL
uses pilot-private schemas and per-run ids. Existing legacy tables are untouched.
The recorder defect remains open for a person to fix `record.py` and explicitly
re-record goldens: this driver preserves observed setup codes, not the defect's
intended contract. S16 normal-path parity can now pass; S12's excluded legacy
abend/error SYSOUT requirement remains partial. The standards checklist lacks
an explicit line distinguishing setup-job provenance from converted-step RCs.
