---
name: carddemo-pitfalls
description: Diagnose build, Spring Batch, record codec and parity failures encountered in the CardDemo estate.
---

# CardDemo pitfalls

Use these entries to diagnose failures in [build](../carddemo-ticket-build/SKILL.md) or [verify](../carddemo-ticket-verify/SKILL.md). See the stream annex for the original symptoms, replay history and stream-specific remedies.

## 1. Maven stops in validate

An enforcer message about the allowed JDK range means Maven started with a different JDK. Use the annex's required JDK and export its `JAVA_HOME` in the current shell. The expected log contains `RequireJavaVersion passed`.

## 2. Testcontainers cannot find Docker

- Symptom: `Could not find a valid Docker environment`, with `client version 1.32 is too old. Minimum supported API version is 1.40`.
- Cause: Testcontainers 1.20.x speaks an API older than the Docker daemon here (1.56) accepts.
- Fix: keep `testcontainers.version` 1.21.4 in `pom.xml` (no override). The log then shows `Testcontainers version: 1.21.4` and `BUILD SUCCESS`.

## 3. Every integration test class after the first loses JDBC

A static `@Container` in an abstract base can stop after the first class while a cached Spring context retains its JDBC URL. Use one singleton container started in a static block. Reported in the initial conversion.

## 4. Docker Hub HTTP 429 on image pulls

Anonymous pulls can hit the rate limit for the estate and Testcontainers. Use the Ubuntu base mirror and local tags under build step 3. Reported in the initial conversion.

## 5. Maven offline mode fails to resolve the enforcer

The plugin is not in the local repository yet. Run the build online once (build step 4). Reported in the initial conversion.

## 6. Wrong amount from a zoned field

The overpunch byte includes the last digit (`{` = +0). Dropping it produces 123.45 instead of the correct 1234.50. Generic decoder example:

```sh
python3 -c 'from tools.parity.copybook import _decode_display; print(_decode_display(b"00000012345{", 2, True, "ascii"))'
```

The proof printed `1234.50`. Apply standards §3 and §4.

## 7. Only rc.json differs

Parity checks SYSOUT and the entire RC map. A direct candidate launch can omit the real setup RCs. Use verify step 2 and compare with the golden's recorded job and step structure (S16). Keep the golden frozen.

## 8. A Spring Batch flow with custom exit statuses ends FAILED although every step succeeded

Custom step exit values need explicit transitions for every step. Provide the expected branches and a terminal catch-all transition (`on("*")`) as in the annex example. Examine the step exit status when the job reports an unmatched transition.

## 9. Review findings

Use S05 for partial commits, S09 for PARM width, S14 for masked operational events, S07/S08 for stable numeric digits and filler bytes, and S13 for recovery and stale artifacts. See the annex for the fixes observed in review.
