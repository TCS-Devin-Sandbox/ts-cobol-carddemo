---
name: interest-ticket-build
description: Build one INTCALC interest ticket (INT-01 to INT-06) in TCS-Devin-Sandbox/ts-cobol-carddemo - the module layout the INT-01 pilot created, the Java and tool versions, and the exact commands to start PostgreSQL, build and run the tests. Use at the start of every interest ticket session; then prove the ticket with interest-ticket-verify.
---

# Interest ticket build

Every command below was run from the repository root on a clean checkout and succeeded; the
quoted line is the output that shows it. What the code must look like (naming, types, numbers,
encodings, quirks, rule-id citation, tests) is in
[interest-conversion-standards](../interest-conversion-standards/SKILL.md); this skill only
builds and tests. Known failures: [interest-pitfalls](../interest-pitfalls/SKILL.md).

## Layout the pilot created

| Path | What it is |
|---|---|
| `java/interest/interest-batch/pom.xml` | The one shared Maven module (`com.carddemo.interest:interest-batch:0.1.0-PILOT`). Tickets 02-06 add to it; its build config is frozen after INT-01. |
| `java/interest/interest-batch/src/main/java/com/carddemo/interest/<area>/` | Ticket code. The pilot's area is `pilot`; a ticket's areas are the `**/<area>/**` globs in its **Scope** line (`schema`, `codec`, `intake`, `job`, `calc`, `handoff`, `gate`). |
| `java/interest/interest-batch/src/main/resources/<area>/` | SQL and properties (pilot: `pilot/schema-pilot.sql`, `pilot/schema-batch.sql`, `pilot/pilot.properties`). |
| `java/interest/interest-batch/src/test/java/com/carddemo/interest/<area>/` | `*Test.java` run by Surefire (`mvn test`), `*IT.java` by Failsafe (`mvn verify`). |
| `java/interest/interest-batch/src/test/resources/pilot/parity/run_case.py` | Parity driver: real setup jobs, then the Java jar in place of INTCALC STEP15 (used by interest-ticket-verify). |
| `java/interest/evidence/<ticket file name without .md>/` | Where this ticket's verification evidence goes (interest-ticket-verify). |

A ticket's **Scope** line is written relative to `java/interest/` (`interest-batch/...`).

## Versions (from `pom.xml` and the replay)

Java 21 (enforcer `[21,22)`), Maven 3.9.9, Spring Boot parent 3.3.5 (Spring Batch 5.1.2),
PostgreSQL JDBC from the Boot BOM, Testcontainers 1.21.4 with image `postgres:15-alpine`,
compose `db` service `postgres:15` (server 15.20), host Python 3.12 for the comparator.

## Steps

1. Start on the revision for your task:
   - **New ticket:** branch from the ticket's dependency base, which must already contain the
     INT-01 Maven module. For INT-02 and INT-03, use `demo/05-pilot` (or its successor
     containing INT-01 and these skills); for INT-04 through INT-06, use the revision with
     the prerequisites listed under **Dependencies** in the ticket. Do not start from
     `demo/04a-standards`: it predates the pilot module.
   - **Pilot replay:** start from a clean checkout of `demo/05a-skills` and stay on that
     revision for the entire replay. `demo/04a-standards` is only the diff base in step 6.
   From the repository root, confirm the checked-out branch and module:
   ```sh
   git branch --show-current && ls java/interest/interest-batch/pom.xml
   ```
   Pilot replay output: `demo/05a-skills`, then `java/interest/interest-batch/pom.xml`.
2. Install Java 21 if `java -version` is not 21 (the image ships 17):
   ```sh
   sudo apt-get install -y -q openjdk-21-jdk-headless
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
   mvn -v
   ```
   Success: `Setting up openjdk-21-jdk-headless:amd64 (21.0.13.1+2-1~22.04.1) ...`, then
   `Apache Maven 3.9.9` and `Java version: 21.0.13.1`. `JAVA_HOME` is per shell: export it in
   every new shell before `mvn`.
3. Start the estate and its PostgreSQL 15 (`db`), compile the legacy programs and confirm the
   legacy interest chain is green before changing anything. On a host without the PostgreSQL
   images, use the mirror to avoid Docker Hub rate limits:
   ```sh
   docker pull mirror.gcr.io/library/postgres:15-alpine &&
     docker tag mirror.gcr.io/library/postgres:15-alpine postgres:15-alpine &&
     docker pull mirror.gcr.io/library/postgres:15 &&
     docker tag mirror.gcr.io/library/postgres:15 postgres:15
   ```
   Success: `Status: Downloaded newer image for mirror.gcr.io/library/postgres:15-alpine`
   and `Status: Downloaded newer image for mirror.gcr.io/library/postgres:15`; both tags exit 0.
   ```sh
   export ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04
   make up && make build && make parity CHAIN=interest
   ```
   Success: `Container ...-db-1 Healthy`, `Container ...-estate-1 Healthy`,
   `CBACT04C: compiled`, and seven lines `interest/<case> [legacy rerun]: PASS (...)`. The
   CBEXPORT/CBIMPORT skips printed by `make build` are expected.
4. Build and run all tests (unit tests, then PostgreSQL 15 integration tests in Testcontainers;
   Docker must be running). In Bash, keep the full output for the evidence (use your ticket
   file name without `.md` in place of `01-pilot` for a new ticket):
   ```sh
   set -o pipefail
   T=01-pilot
   mkdir -p "java/interest/evidence/$T" &&
     mvn -B -f java/interest/interest-batch/pom.xml clean verify 2>&1 |
     tee "java/interest/evidence/$T/test-output.txt"
   ```
   Success at the pilot: `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0` (Surefire),
   `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` (Failsafe), `BUILD SUCCESS`. A later
   ticket's counts are higher by its own tests; zero failures, errors and skips is the pass.
   This also builds `target/interest-batch-0.1.0-PILOT.jar`, which the parity driver runs.
   FAILED jobs and stack traces logged by negative-path ITs are expected; read the totals.
5. Run the parity driver's own unit test:
   ```sh
   python3 -m unittest discover -s java/interest/interest-batch/src/test/resources/pilot/parity -v
   ```
   Success: `Ran 1 test` and `OK`.
6. Check that the code diff stays inside the ticket's Scope and touches nothing frozen
   (standards §8, S17/S18). `<base>` is the base branch of step 1; the pilot's was
   `demo/04a-standards`: `git diff --stat origin/<base> -- java/interest`. Every listed path must
   match the ticket's Scope or be under `java/interest/evidence/<ticket>/`. `target/` is build
   output and is not committed.
7. Prove the ticket with [interest-ticket-verify](../interest-ticket-verify/SKILL.md).
