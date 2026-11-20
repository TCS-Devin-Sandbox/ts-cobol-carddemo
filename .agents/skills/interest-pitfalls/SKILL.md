---
name: interest-pitfalls
description: The failures the INT-01 interest pilot actually hit in TCS-Devin-Sandbox/ts-cobol-carddemo while building and proving the CBACT04C conversion, each as symptom, cause and fix. Use when a step of interest-ticket-build or interest-ticket-verify fails, before debugging from scratch.
---

# Interest pitfalls

Only failures the pilot hit (its report:
`java/interest/interest-batch/src/test/resources/pilot/evidence/PILOT-REPORT.md`, section
"Historical direct-jar method and failures", plus its review rounds). Each was reproduced in
Step 5a with the quoted output unless marked **reported by the pilot, not reproduced**. Where
the fix is a standard, the section of
[interest-conversion-standards](../interest-conversion-standards/SKILL.md) is named and is the
whole fix.

## Steps

1. **Maven stops in `validate` on Java 17.**
   - Symptom: `RequireJavaVersion failed with message: ... Detected JDK version 17.0.20-1 ... is not in the allowed range [21,22).`
   - Cause: the image ships Java 17; the module requires 21.
   - Fix: interest-ticket-build step 2. Then
     `mvn -B -f java/interest/interest-batch/pom.xml validate` prints `RequireJavaVersion passed` and `BUILD SUCCESS`.
2. **Testcontainers cannot find Docker.**
   - Symptom: `Could not find a valid Docker environment`, with
     `client version 1.32 is too old. Minimum supported API version is 1.40`; ITs end
     `Errors: 2` and `BUILD FAILURE` (reproduced with `-Dtestcontainers.version=1.20.3`).
   - Cause: Testcontainers 1.20.x speaks an API older than the Docker daemon here (1.56) accepts.
   - Fix: keep `testcontainers.version` 1.21.4 in `pom.xml` (no override); the log then shows
     `Testcontainers version: 1.21.4` and `BUILD SUCCESS`.
3. **Every IT after the first fails with "Could not open JDBC Connection"** (30 s timeouts) -
   reported by the pilot, not reproduced.
   - Cause: a `@Container` static field in an abstract base class is stopped after the first IT
     class while the cached Spring context keeps its URL.
   - Fix: one singleton container started in a static block, as
     `src/test/java/com/carddemo/interest/pilot/PilotPostgresTestSupport.java` does.
4. **Docker Hub HTTP 429 on image pulls** (`make up`, Testcontainers) - reported by the pilot,
   not reproduced.
   - Cause: anonymous Docker Hub rate limit.
   - Fix (pilot): build the estate with `ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04`,
     and pull other images from `mirror.gcr.io` and `docker tag` them to the Docker Hub name.
5. **`mvn -o` fails resolving the enforcer plugin** - reported by the pilot, not reproduced.
   - Cause: the plugin is not in the local repository yet.
   - Fix: run the build online once (interest-ticket-build step 4).
6. **A test expects the wrong amount from a zoned field.**
   - Symptom: test data read `00000012345{` as `123.45`.
   - Cause: the overpunch byte is the last digit (`{` = +0), not a sign to drop:
     `python3 -c 'from tools.parity.copybook import _decode_display; print(_decode_display(b"00000012345{", 2, True, "ascii"))'`
     prints `1234.50`.
   - Fix: standards §3 and §4.
7. **compare.py fails only on `rc.json`.**
   - Symptom: `interest/i01-baseline [java]: FAIL (4 records, 50 fields, 1 differences)`; the
     report row is `| rc.json | (text) | ...` with golden steps `STEP05` to `STEP30` against
     the actual `{"INTCALC":{"maxrc":0,"steps":{"STEP15":0}}}`.
   - Cause: the comparator also checks `rc.json` and SYSOUT, and the golden `rc.json` lists the
     setup jobs' step codes under INTCALC (recorder finding 1 in the pilot report, OPEN). Running
     the jar alone yields only STEP15.
   - Fix: run each case through the driver (interest-ticket-verify step 2), which executes the
     real setup jobs and uses their observed codes; standards §7 (S16). Never edit the golden.
8. **Review findings on the pilot** (PILOT-REPORT, "Review fixes" and "Review round 2"):
    chunked commits that could leave a partial run (fix: standards §2, S05), PARM not checked as
    ten bytes (§3, S09), no log event for the DISCGRP fallback (§6, S14), locale-dependent digits
    and lost filler bytes (§3), stale artifacts after a failed rerun into a reused directory
    (§6). Reported by the pilot, fixed in its code, not reproduced.
