---
name: carddemo-ticket-build
description: Start the CardDemo estate, build a batch conversion and collect its test evidence.
---

# CardDemo ticket build

Read the [standards](../carddemo-conversion-standards/SKILL.md), the [pitfalls](../carddemo-pitfalls/SKILL.md) and the stream annex. The annex supplies the layout, versions, scope root and shell values. Run the steps from the repository root in the same Bash session. The variables `STREAM`, `MODULE`, `SCOPE`, `TICKET`, `BASE_REF`, `JAVA_HOME`, `DRIVER_TEST_DIR` and `EVIDENCE` come from the annex or ticket. `STREAM` is the `<stream>` name appearing in `fixtures/<stream>/`.

## Step 1 · Revision and layout

For a new ticket, use its dependency base with the established build module. For a replay, use a clean checkout of the specified revision and stay there throughout. The diff base may differ from the checked-out revision. Ticket Scope is relative to the annex's scope root.

```sh
git branch --show-current && ls "$MODULE/pom.xml"
```

The proof printed the checked-out branch and the module POM path. See [full output](../../../modernization/design/evidence/skills-shared-check.txt).

## Step 2 · Toolchain

Use the JDK required by the module. If needed, install it using the annex's platform instructions. Export `JAVA_HOME` in every new shell before Maven.

```sh
export JAVA_HOME
mvn -v
```

The proof printed `Apache Maven 3.9.9` and `Java version: 21.0.13.1`.

## Step 3 · Estate and legacy baseline

Use these mirror pulls and local aliases on a host without the PostgreSQL images. The Alpine tag is also used by the PostgreSQL integration tests.

```sh
docker pull mirror.gcr.io/library/postgres:15-alpine &&
  docker tag mirror.gcr.io/library/postgres:15-alpine postgres:15-alpine &&
  docker pull mirror.gcr.io/library/postgres:15 &&
  docker tag mirror.gcr.io/library/postgres:15 postgres:15
export ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04
make up && make build && make parity CHAIN="$STREAM"
```

The proof printed `Status: Downloaded newer image`, container `Healthy` messages, compiled programs and passing `[legacy rerun]: PASS` cases. Analysis-only components may be skipped by the legacy build.

## Step 4 · Unit and integration tests

Use PostgreSQL 15 Testcontainers integration tests where the ticket requires them. Docker and the test image must be available. Use the annex's JDK and tool settings. Keep the full output and check the Maven summary. Negative-path ITs can log FAILED jobs and stack traces.

```sh
set -o pipefail
mkdir -p "$EVIDENCE" &&
  mvn -B -f "$MODULE/pom.xml" clean verify 2>&1 | tee "$EVIDENCE/test-output.txt"
```

The proof printed `Failures: 0, Errors: 0, Skipped: 0` in both summaries and `BUILD SUCCESS`. The build also produces the candidate jar named in the annex.

## Step 5 · Driver tests

```sh
python3 -m unittest discover -s "$DRIVER_TEST_DIR" -v
```

The proof printed `Ran 1 test` and `OK`.

## Step 6 · Scope

Compare to the ticket's diff base. Each listed path must match the ticket's Scope or evidence directory. Apply S17 and S18 to review frozen paths and interfaces. Leave Maven's generated `target/` out of the commit.

```sh
git diff --stat "$BASE_REF" -- "$SCOPE"
```

The documentation proof produced empty output and exit 0 for the candidate scope.

## Step 7 · Verify

Prove the ticket with [carddemo-ticket-verify](../carddemo-ticket-verify/SKILL.md).
