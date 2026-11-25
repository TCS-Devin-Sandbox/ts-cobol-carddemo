# READCARD — Environment Setup & Execution Report

Skill: `environment-setup-skill` · Host: Ubuntu 22.04.5 LTS · Date: 2026-11-25
Workload: `READCARD.jcl` STEP05 → CBACT02C → `workbench/pyspark_generation/pyspark_application.py` (approved Stage 4 v2, commit `098f871`)

## 1. Prerequisite Matrix

| Component | Required Version | Installed Version | Status |
|---|---|---|---|
| Python | >= 3.10 | 3.12.16 | OK |
| Java | 17 | OpenJDK 17.0.21.1 (`/usr/lib/jvm/java-17-openjdk-amd64`) | OK |
| Spark | 3.5.x | 3.5.1 (Scala 2.12.18, `/opt/spark`) | OK |
| PySpark | Matching Spark | 3.5.1 | OK (pinned to Spark) |
| pytest / pytest-bdd | — | 8.3.3 / 8.1.0 | OK |
| pandas | — | 3.1.1 | OK |
| Cobrix | Compatible version | Not installed | Not required — see below |

**Decoder choice:** the candidate uses the **repository copybook decoder** path, not Cobrix. The READCARD input (`CVACT02Y`, 150-byte, all `PIC X`/unsigned `9` display fields, no COMP-3) is the off-host ASCII fixture text, parsed with the field layout in `pyspark_generation/config.json`; parity is proven with `tools/parity/compare.py`.

## 2. Installation Log

Full log: `install.log`.

| Step | Command | Result |
|---|---|---|
| System packages | `sudo apt-get install -y curl wget git python3 python3-venv python3-pip unzip openjdk-17-jdk-headless` | Installed |
| Spark download | `wget https://archive.apache.org/dist/spark/spark-3.5.1/spark-3.5.1-bin-hadoop3.tgz` | ~400 MB |
| Spark install | `sudo tar -xzf spark-3.5.1-bin-hadoop3.tgz --strip-components=1 -C /opt/spark` | `/opt/spark` |
| Virtual env | `python3 -m venv ~/venvs/pyspark` | `/home/ubuntu/venvs/pyspark` |
| Python deps | `pip install pyspark==3.5.1 pytest==8.3.3 pytest-bdd==8.1.0 pandas` | Installed |
| Env vars | `env.sh`, sourced from `~/.bashrc` | Persisted |

## 3. Environment File (`env.sh`)

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export SPARK_HOME=/opt/spark
export PATH=$JAVA_HOME/bin:$SPARK_HOME/bin:$PATH
export PYSPARK_PYTHON=$HOME/venvs/pyspark/bin/python
export PYSPARK_DRIVER_PYTHON=$HOME/venvs/pyspark/bin/python
```

## 4. Validation Evidence

Full capture: `validation_evidence.txt`.

- `java -version` → `openjdk version "17.0.21.1" 2026-11-17`
- `spark-submit --version` → `version 3.5.1`, Scala 2.12.18, OpenJDK 17.0.21.1
- Smoke test on `fixtures/read-masters/r01-populated/input/*.txt` → schema `value: string`, record count **8** (all four master files of the case)
- `python3 tools/parity/compare.py --help` → usage text printed (comparator available)

## 5. Execution of the Generated PySpark Application

Command (per fixture case):

```sh
source workbench/environment/env.sh && source ~/venvs/pyspark/bin/activate
spark-submit --master 'local[*]' workbench/pyspark_generation/pyspark_application.py \
  --input fixtures/read-masters/<case>/input --out workbench/environment/run_output/<case> \
  --chain read-masters --case <case>
```

| Case | Exit | Records read / listed | RC (`rc.json`) | SYSOUT vs golden | Control total (SHA-256 of records) |
|---|---|---|---|---|---|
| r01-populated | 0 | 2 / 2 | READCARD STEP05 = 0 (golden 0) | Identical (`diff` clean) | `6966bdf3…caa8` |
| r02-empty | 0 | 0 / 0 | READCARD STEP05 = 0 (golden 0) | Identical (`diff` clean) | — |

Final output — `run_output/r01-populated/sysout/READCARD.STEP05.txt`:

```text
START OF EXECUTION OF PROGRAM CBACT02C
400000000000100100000001001000TEST CUSTOMER 1001                                2026-12-31Y                                                           
400000000000100200000001002000TEST CUSTOMER 1002                                2026-12-31Y                                                           
END OF EXECUTION OF PROGRAM CBACT02C
```

Repository parity tool (`tools/parity/run_candidate.py --chain read-masters --steps READCARD`, see `parity_output.txt`):

```text
read-masters/r01-populated [java (READCARD)]: PASS (0 records, 0 fields, 0 differences)
read-masters/r02-empty [java (READCARD)]: PASS (0 records, 0 fields, 0 differences)
```

## 6. Execution of the BDD Feature File

Command: `cd workbench/validation && python -m pytest -v --junitxml=../environment/bdd_junit_results.xml`
Feature: `validation/features/READCARD.feature` · Steps: `validation/steps/READCARD_steps.py`

**Result: 25 passed, 0 failed** (16.0 s; 33 warnings are unregistered tag marks such as `@BR-001`, cosmetic only). Output: `bdd_pytest_output.txt`.

| # | Scenario | Result |
|---|---|---|
| 1 | Every card in the populated golden master is listed | PASS |
| 2 | A single-card master lists exactly one detail line | PASS |
| 3 | A large card master (20,000 records) lists every record | PASS |
| 4 | Unordered input is listed in ascending card-number order | PASS |
| 5 | Card numbers containing letters follow KSDS EBCDIC collation | PASS |
| 6 | Trimmed input records are emitted as 150-character images | PASS |
| 7 | A full 150-character record is emitted unchanged | PASS |
| 8 | Card number and CVV are emitted in clear text exactly as legacy | PASS |
| 9 | The listing is framed by the CBACT02C banners | PASS |
| 10 | An empty card master completes successfully | PASS |
| 11 | A missing card master abends with the open error | PASS |
| 12 | An over-length record abends with the read error before any detail line | PASS |
| 13 | Duplicate card numbers abend with the read error before any detail line | PASS |
| 14 | A failure while closing the listing abends with the close error (D-01) | PASS |
| 15 | End of file ends the read loop normally | PASS |
| 16–21 | File status display format (35→0035, 34→0034, 22→0022, 9/x04→9004, 9/x22→9034, 90→9048) | 6 × PASS |
| 22 | The card master is opened read-only | PASS |
| 23 | The listing carries no count or trailer lines | PASS |
| 24 | Run metrics reconcile with the legacy SYSOUT | PASS |
| 25 | A Parquet card master produces the same listing as the fixed-width master | PASS |

## 7. Known Issues

| Issue | Resolution | Final Status |
|---|---|---|
| `downloads.apache.org` only hosts the latest 3.5.x; PySpark is pinned to 3.5.1 | Downloaded 3.5.1 from `archive.apache.org` to keep Spark = PySpark | Resolved |
| Slow Spark mirror download (~1 h for 400 MB) | Waited; consider caching the tarball in the environment blueprint | Resolved |
| Cobrix not installed | Not needed for READCARD (display-only ASCII fixture); repository decoder used | N/A |
| Java 21 is also present on the host | `env.sh` pins `JAVA_HOME` to Java 17 | Resolved |
| Devin Review on PR #75: Latin-1 bytes corrupted in fixed-width read; Parquet over-width fields silently truncated by `rpad`; output/metrics write failures bypass the abend path | Escalated — requires a Stage 4 v3 + Stage 5 rerun under the approval gate | Open |

No files under `fixtures/` or `app/` were modified.
