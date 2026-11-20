---
name: carddemo-stream-interest
description: Interest stream source, contracts, commands and evidence for the shared CardDemo skills.
---

# CardDemo interest stream annex

Use with [standards](../carddemo-conversion-standards/SKILL.md), [build](../carddemo-ticket-build/SKILL.md), [verify](../carddemo-ticket-verify/SKILL.md) and [pitfalls](../carddemo-pitfalls/SKILL.md).

## Stream inventory

| Item | Interest binding |
|---|---|
| Conversion | [CBACT04C](../../../app/cbl/CBACT04C.cbl), [INTCALC](../../../app/jcl/INTCALC.jcl) STEP15, PARM `2022071800`; setup DEFGDGB, ACCTFILE, XREFFILE, TCATBALF and DISCGRP. |
| Input | `AWS.M2.CARDDEMO.` prefix on the fixture sequential inputs `ACCTDATA.PS` (300), `CARDXREF.PS` (50), `TCATBALF.PS` (50), `DISCGRP.PS` (50). See [chain manifest](../../../tools/runjcl/chains.json). |
| JCL inputs/DD | Same prefix on `ACCTDATA.VSAM.KSDS` / ACCTFILE, `TCATBALF.VSAM.KSDS` / TCATBALF, `DISCGRP.VSAM.KSDS` / DISCGRP and `CARDXREF.VSAM.KSDS` / XREFFILE with alternate `CARDXREF.VSAM.AIX.PATH` / XREFFIL1. |
| Output | ACCTFILE rewrites, TRANSACT `AWS.M2.CARDDEMO.SYSTRAN(+1)` (350/F, NEW/CATLG/DELETE; compare `(0)`), `INTCALC.STEP15.txt` SYSOUT and `rc.json`. |
| Copybooks | `CVACT01Y` account, `CVACT03Y` XREF, `CVTRA01Y` category balance, `CVTRA02Y` disclosure group and `CVTRA05Y` emitted transaction. |
| Rules and design | `INT-` (`INT-R`, `INT-Q`, `INT-H`), plus technical notes `TN-`. [Rules](../../../modernization/rules/interest/rules.yaml), [OPEN decisions](../../../modernization/rules/interest/decisions.md), [INT-ARCH](../../../modernization/design/architecture.md), [INT-MAP](../../../modernization/design/data-map-interest.md), [tickets INT-01 to INT-06](../../../modernization/design/tickets/README.md). |
| Toolchain | Java 21 (enforcer `[21,22)`), Maven 3.9.9, Spring Boot 3.3.5, Spring Batch 5.1.2, Boot BOM PostgreSQL JDBC, Testcontainers 1.21.4 (`postgres:15-alpine`), Compose `db` (`postgres:15`), host Python 3.12 in the original replay. |

### Cases

[Recorded fixtures](../../../fixtures/interest). The estate freezes `2022-07-05 10:00:00.00`.

| Case | Recorded focus |
|---|---|
| `i01-baseline` | Two accounts, only the first rewritten |
| `i02-tie-half-cent` | Stored cents truncate at ties |
| `i03-zero-rate-skipped` | Zero rate produces no transaction |
| `i04-default-group-fallback` | Missing exact group uses padded DEFAULT |
| `i05-multi-category` | Multiple category balances in one account |
| `i06-negative-balance` | Signed interest, sole account unchanged |
| `i07-nontie-rounding` | Truncation beyond the half-cent cases |

## Combined replay inputs

Start from the clean checkout being verified. Use a distinct Compose project and a scratch evidence directory. For ordinary tickets, substitute the assigned ticket and its required base/evidence paths.

```sh
STREAM=interest
SCOPE="java/$STREAM"
MODULE="$SCOPE/interest-batch"
TICKET=05c-combined
BASE_REF=origin/demo/05b-integration
EVIDENCE="work/$TICKET/evidence"
WORK_ROOT="work/$TICKET/cases"
P="$MODULE/src/test/resources/pilot"
DRIVER_TEST_DIR="$P/parity"
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
unset JDK_JAVA_OPTIONS
export COMPOSE_PROJECT_NAME=carddemo-05c-proof
```

If Java 21 is missing on Ubuntu 22.04, install it before shared build step 2.

```sh
sudo apt-get install -y -q openjdk-21-jdk-headless
```

For this replay, pre-cache the Testcontainers cleanup image if it is missing.

```sh
docker pull testcontainers/ryuk:0.12.0
```

Define the shared verify adapter in the same shell. It replaces INTCALC STEP15 with the joined Java job. It uses the fixture inputs, real legacy setup jobs and Compose PostgreSQL. The jar is `$MODULE/target/interest-batch-0.1.0-PILOT.jar`.

```sh
run_candidate() {
  docker compose exec -T \
    -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
    -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
    estate python3 "$P/parity/run_case.py" "$1" --out "$2" \
    --java "$MODULE/scripts/parity/intcalc_java.py"
}
```

The integrated module's PostgreSQL ITs run its native DB assertions. The historical pilot standalone SQL check uses the pilot schema and the pilot driver without the joined wrapper. Its command and known output are under Verify 5 below. The combined replay compares all seven candidate cases and runs the whole legacy estate suite.

## Original interest standards and evidence

**PROPOSED · Owner: Target architect.** The pilot, [INT-01](../../../modernization/design/tickets/01-pilot.md), applied these standards once. Corrections that only match its code are applied, and the rest are open in [Step 5a §4](../../../modernization/design/skills.md#4-proposed-corrections-to-interest-conversion-standards-target-architect-decides). The [reader summary](../../../modernization/design/standards.md) is explanatory. The interest examples and source citations below preserve the original standards in their original wording. The shared skill supplies the reusable rule and this annex supplies the interest binding.

### Standard S01

1. **S01 · Scope, then shape, then source.** Read the assigned [ticket](../../../modernization/design/tickets/README.md), [rules](../../../modernization/rules/interest/rules.yaml) and [OPEN decisions](../../../modernization/rules/interest/decisions.md) for the what and why. Read [INT-ARCH](../../../modernization/design/architecture.md) and [INT-MAP](../../../modernization/design/data-map-interest.md) for the target. Before implementing a rule, read its cited COBOL lines and relevant copybook. **Do** trace INT-R03 through D00–D03 and CBACT04C 415–460. **Do not** implement a fallback from the JCL comment.

### Standard S02

2. **S02 · Recorded legacy output decides parity.** Compare with the committed [goldens](../../../fixtures/interest) using [carddemo-parity](../carddemo-parity/SKILL.md). When prose and COBOL disagree, reproduce the observed code behaviour and send the ID, source lines, affected fields, fixture evidence and discrepancy to the decision-register owner. Hold unresolved live behaviour for evidence. **Do** use i06's negative transaction and unchanged account for INT-Q03/Q02. **Do not** treat INT-Q03 or the fixture README as authority to debit the final account.

### Standard S03

- **S03 · Model rules as business operations.** Use small rule-driven functions and typed results for lookup, skip, group completion and failure (INT-R02–R04/R09/R10). **Do** return a typed exact/default rate result and fail a missing required rate. **Do not** create a method per COBOL paragraph, emulate GO TO or PERFORM THRU, mirror WORKING-STORAGE in a class, or use flags/switches in place of a result or type.

### Standard S04

- **S04 · Use immutable business records.** Map the five copybooks to records named for the business data. Name each COBOL field at its data-map/codec binding once. Keep raw bytes immutable when retained (A13/X04/B05/D05/T14). **Do** use `CategoryBalance.balance` and bind `TRAN-CAT-BAL` once at B04. **Do not** pass mutable `CVTRA01Y` buffers through processing.

### Standard S05

- **S05 · Follow the interest Spring Batch boundaries.** Use a reader for ordered account groups from the sealed snapshot, keyed lookups and processing for rule outcomes, and a writer for private transactions, eligible account delta and checkpoint in one PostgreSQL transaction per group. A ticket that excludes restart may commit the whole run as one transaction so a failure commits nothing (the pilot: `PilotJobConfig.WHOLE_RUN`); restart tickets commit per group. Lookahead establishes the next balance key. Evaluate the next group's account/card/rate lookups after the previous group completes, following CBACT04C 193–213. Use INT-ARCH A1–A4 and the ticket scope (INT-R06/R09, INT-Q02, INT-H06). **Do** keep external handoff outside compute and retrying chunks. **Do not** checkpoint by Spring's offset independently or publish from an item writer.

### Standard S06

- **S06 · Inject collaborators through constructors.** Keep mutable execution state per run or account (INT-R08/R09, INT-Q05). **Do** constructor-inject the rate lookup and timestamp source and recover the ordinal from the checkpoint. **Do not** share a mutable static counter, clock, cached account or record buffer.

### Standard S07

- **S07 · Assign exact decimals at each legacy receiver.** Use decimal literals and `BigDecimal` throughout for money and rates. B04/T06 and monthly/total working amounts are precision 11, scale 2. D04 is precision 6, scale 2. A03–A05/A09–A10 are precision 12, scale 2. Multiply and add exactly with no intermediate rounding, divide by exact 1200 at scale 2 with `RoundingMode.DOWN`, and state exactness or a rounding mode at every arithmetic/assignment step (use `UNNECESSARY` for values already exact at scale 2). Apply the receiver's high-digit limit on each COBOL assignment/addition, preserving sign in signed receivers. Decode original values without rounding. See INT-R01/R06, INT-Q01 and INT-H05. **Do** produce 12.35 from 12.355 before totaling. **Do not** use `double`, `float`, an intermediate precision cap, half-even, or PostgreSQL coercion for arithmetic.

### Standard S08

- **S08 · Decode and encode only at the boundary.** Use INT-MAP X/D/Z/B and the verified transfer profile for EBCDIC, DISPLAY digits and zoned overpunch. Keep filler/raw bytes and encoded keys as mapped. The five layouts contain zero packed or binary fields. Isolate the binary diagnostic overlay in the status adapter (TN-04). **Do** hand `BigDecimal("-3.00")` from T06 to the application. **Do not** interpret `}` in interest processing or add a COMP-3 decoder for this stream.

### Standard S09

- **S09 · Inject separate cycle and wall-clock inputs.** Bind the exact 10-character/byte-compatible PARM as opaque text and inject the clock and time zone (freeze i01–i07 at `2022-07-05 10:00:00.00`). Take one local reading per emitted row and use it for both timestamps. Keep A06–A08 as fixed text (INT-H02, INT-Q05, T01/T12–T13). **Do** use `2022071800` for the ID prefix and the injected clock for `2022-07-05-10.00.00.000000`. Hold the run when the PARM is not 10 printable bytes in the profile (INT-H02; the pilot: `TransactionIdSource`). **Do not** parse the PARM into a timestamp or call the system clock from the job.

### Standard S10

- **S10 · Keep one named implementation point and a rule-named test for each QUIRK or OPEN decision.** Cite the full ID in code and in the test name (underscores may replace hyphens). Preserve legacy option A where evidenced, and trace every emitted, skipped or held result to a rule or technical note. No silent fixes or tidying that changes output. **Do** keep the EOF disposition in one named path marked INT-Q02 (the pilot: `InterestGroupProcessor.process`, which builds `AccountDelta.closing` only on an account break) with a test such as `INT_Q02_finalAccountAtEofHasNoDelta`. **Do not** scatter last-account exceptions through exporters or quietly enable the alternate decision.

### Standard S11

- **S11 · Report source and wording gaps.** Refer INT-Q03 and the READMEs for i01, i05 and i06 to Finance / card operations and interest policy where they imply increases/decreases on the final account. CBACT04C 188–222 and the account goldens govern. For INT-H06, earlier ACCTDATA rewrites can persist, but J 37–41 requests DELETE of the new generation on abnormal end. Treat a surviving partial generation and scheduler resubmission as host questions for Operations / application, with all decisions OPEN. **Do** report the discrepancy and exercise the contained failure boundary in INT-ARCH A2–A4. **Do not** alter the register or model a partial generation as consumable.

### Standard S12

- **S12 · Map statuses by operation, then map exit and diagnostics.** The names below describe typed outcomes for INT-R10/H08 and TN-03/04/07/09. The ticket implementation follows the A2 job contract. **Do** map DISCGRP exact-read 23 to DEFAULT and its second miss to U999/RC 12. **Do not** return RC 12 for normal EOF or reuse that RC as proof of an infrastructure fault's legacy status.

| Source case | Java result / exception | Job exit and ordinary log event |
|---|---|---|
| `00` on all five opens/closes, reads, ACCTFILE REWRITE and TRANSACT WRITE (P 234–611) | Successful result, no exception | COMPLETED/STEP15 RC 0 on normal close. Log completion once with run/step and counts. Live completion is gated by A4. |
| `10` on TCATBALF sequential READ only (P 325–347) | End of input, no exception | Continue to normal close and RC 0 if successful. EOF event may carry the processed count only. |
| `23` on first DISCGRP READ (P 415–440) | Missing exact rate result, execute one padded DEFAULT lookup | Continue if DEFAULT returns 00. Log a fallback event with DD/operation/status. Write `DISCLOSURE GROUP RECORD MISSING` then `TRY WITH DEFAULT GROUP CODE` to restricted SYSOUT for the invalid key. |
| Account/XREF invalid key, or DEFAULT READ returns any non-00 incl. `23` (P 372–413, 443–460) | Typed required-lookup failure with DD, operation and status | FAILED with U999 outcome and harness STEP15 RC 12. Log failed lookup without an identifier. |
| Any other non-00 status, numeric or extended, incl. on OPEN/CLOSE, READ, WRITE or REWRITE (P 234–611, 635–648) | Typed legacy I/O failure with DD, operation and status | Same FAILED/U999 and RC 12 mapping. Log file/operation/status. Pair with exact legacy diagnostic, formatter and abend lines in restricted SYSOUT. No ready packet. |
| Intake/profile, PostgreSQL, packaging or bridge failure without a matching legacy condition (INT-ARCH A1–A4) | Typed target validation, infrastructure or handoff failure | FAILED or HELD per boundary, with separate target reason and no success release. Log phase/category, no invented COBOL file status. |

### Standard S13

- **S13 · Preserve private restart and hold uncertain publication.** Commit the next-key/last-account/last-ordinal checkpoint with each group's rows and delta. Use the same sealed snapshot, revision and parameters. Roll back the entire uncommitted group and keep already committed rows/clocks. Resume failed private work only through the explicit A3 recovery path. Rebuild a partial intake on a new attempt or verify the exact manifest before resuming. Recreate packages from committed stage data by run/hash. Treat host recovery as manual and fenced pending INT-H06. **Do** reconcile the journaled account images and absolute GDS/digest after an unknown write before any retry. **Do not** relaunch a live run, advance the GDG or release COMBTRAN blindly.

### Standard S14

- **S14 · Keep diagnostic evidence out of ordinary logs.** Use run, phase, DD, operation, status, reason and counts. Write exact restricted SYSOUT in source order, including the lookahead balance DISPLAY before the prior-account rewrite. Preserve the operation-specific text at P 234–611 (including `ERROR OPENING DALY REJECTS FILE`). In the status adapter, use `FILE STATUS IS: NNNN0023` for a terminal numeric 23. For a status beginning with 9 or containing nondigits, reproduce P 635–648 by copying the first character and rendering the reinterpreted binary value in three decimal digits using the evidenced profile. Follow with `ABENDING PROGRAM` and the lab stub's exact abend line when called (TN-01/03/04/07/09). **Do** log `event=rate-fallback run=… dd=DISCGRP op=READ status=23`. **Do not** log card/account identifiers, raw records, balances or unsanitized exception messages.

### Standard S15

- **S15 · Give every rule an independent assertion.** Across the stream require at least one named test per INT-R/INT-Q/INT-H ID and cover applicable TNs. In each ticket cover its rules, including the cases its rule lists and synthetic gap cases for rules without fixtures. **Do** use i02 and i07 outputs for INT-Q01 and a synthetic nonzero-rate/0.00 transaction for INT-Q04. **Do not** use i03's zero rate as the INT-Q04 test or assert against a copy of the candidate's output.

### Standard S16

- **S16 · Run PostgreSQL 15 integration and compare candidate evidence.** Use a container for schema, ordering, transactions and restart tests, and `mvn clean verify` where specified. Start with INT-01/i01, then satisfy the remaining ticket verification and all seven cases at INT-06. Use the unchanged comparator against committed dataset fields, SYSOUT and RC, with byte/filler/framing tests separately. Produce the RC evidence by running the real setup jobs and substituting only the converted step, whose RC is the candidate's process exit code (the pilot: `src/test/resources/pilot/parity/run_case.py`); the golden `rc.json` lists the setup steps under INTCALC. **Do** check natively persisted order, signed sums, unchanged last account, rollback and no replay of committed ordinals where in scope. **Do not** substitute H2 or call matching rounded totals parity.

### Standard S17

- **S17 · Freeze the legacy and evidence inputs.** Keep `app/`, `fixtures/`, goldens and `tools/parity/` untouched. Never re-record for INTCALC (INT-R10 and TN-01 use those oracles). **Do** put a missing-DEFAULT specimen in ticket-owned test resources. **Do not** adjust i04 golden SYSOUT or the comparator to make candidate results pass.

### Standard S18

- **S18 · Freeze shared interfaces and justify dependencies.** Respect the ticket scope and INT-ARCH/INT-MAP contracts, including staged A03/A09/A10, B00, T01–T14 and TN-08's reference-schema boundary. State why any new dependency is needed in the PR. **Do** justify the PostgreSQL 15 test dependency in the bootstrap PR. **Do not** change `carddemo.*`, the frozen codec contract or a shared table from a consuming ticket.

### Standard S19

- **S19 · Synthetic data only.** Use only synthetic A01/X01/T11 and dataset contents in repo inputs, tests, outputs and PR attachments (TN-01 and INT-MAP). **Do** use the committed i01 fixtures. **Do not** copy a real host record, hash derived from a real account ID, or production SYSOUT into a PR.

### COBOL behaviours

Evidence keys: **P** = [app/cbl/CBACT04C.cbl](../../../app/cbl/CBACT04C.cbl), **J** = [app/jcl/INTCALC.jcl](../../../app/jcl/INTCALC.jcl), **XJ** = [app/jcl/XREFFILE.jcl](../../../app/jcl/XREFFILE.jcl), **B** = [tools/build.sh](../../../tools/build.sh), **LE** = [tools/stubs/CEE3ABD.cbl](../../../tools/stubs/CEE3ABD.cbl). Copybook names refer to files under `app/cpy/`. Cited ranges are inclusive. Fixture references are committed [interest cases](../../../fixtures/interest). Tests in the table are requirements for the ticket that owns the behaviour. Synthetic probes use independent expected values and are labelled when the recorded cases do not exercise the path.

<!-- BEGIN COBOL BEHAVIOURS -->
| ID · Behaviour | Evidence and presence | Legacy behaviour | Java standard and example | Test and host confirmation |
|---|---|---|---|---|
| a · Decimal truncation | **PRESENT** · P 168–169, 352–354, 464–467 | The cent receiver omits `ROUNDED`, so it cuts extra fractional digits toward zero before accumulation. | INT-Q01. Do divide at scale 2 with `DOWN`. Do not use `FLOOR` or nearest-cent rounding. | i02-tie-half-cent verifies 12.355 → 12.35 and 12.345 → 12.34, including the first account update. i07-nontie-rounding for non-ties and a synthetic negative sub-cent remainder to distinguish DOWN from FLOOR. |
| b · Silent loss of high digits | **PRESENT** · P 168–173, 352, 464–467, 474 (no `ON SIZE ERROR`) | Over-width DISPLAY receivers keep the low digits. The six-digit suffix wraps. The unreported nine-digit record count is distinct. | INT-H05/R08. Do apply signed unscaled remainder at each receiver's precision and use the emitted ordinal modulo 1,000,000 for T01. Do not let SQL overflow decide the amount or deduplicate by T01. | Synthetic width/carry probes at monthly, total and ACCT receivers, plus ordinal 1,000,000 → 000000 with distinct staged rows and separate collision preflight. **Confirm on the mainframe** · Application / COBOL compiler owner, using deployed options and boundary vectors. |
| c · Intermediate `COMPUTE` precision | **PRESENT** · P 168–169, 464–467 and B 16 | One expression multiplies two fixed decimals before dividing by 1200. Only the resulting monthly receiver is visible. Compiler intermediate precision can differ. | INT-R01/Q01/H05. Do use exact product, then divide once into the cent receiver and apply its width. Do not narrow the product to the destination PIC. | i02 and i07 plus a synthetic near-width and repeating-quotient vector. **Confirm on the mainframe** · Application / COBOL compiler owner, deployed Enterprise COBOL `ARITH` and boundary outputs. `-std=ibm` enables off-host compatibility. |
| d · Unsigned fields and zoned overpunch | **PRESENT** · CVTRA01Y.cpy 6–10, CVTRA02Y.cpy 6–10, CVTRA05Y.cpy 7–11 and P 474–491 and B 16 | Signed DISPLAY carries a trailing embedded sign. Unsigned destination fields (category, merchant and suffix) have digits only. No signed-to-unsigned move of a negative business amount was found. | B04/D04/T03/T06/T07, INT-Q03. Do decode signed amounts and preserve the sign in T06. Do not accept a signed digit in an unsigned key or send an overpunch to the processor. | i06-negative-balance yields −3.00 in T06. Byte tests cover unsigned zero, negative and signed-zero round-trips. **Confirm on the mainframe** · Host data / VSAM and COBOL compiler owners for sign zones, signed-zero handling and deployed `NUMPROC` assumptions. |
| e · Packed, binary and compile-option truncation | **PRESENT** for internal binary only · P 125–139, 175–178, 630–642 and B 16. The five copybook record layouts contain no COMP-3, PACKED-DECIMAL or binary usage | COMP/BINARY holds internal result, LINKAGE length, LE arguments and a two-byte diagnostics value. Current values need no wider binary arithmetic. Host `TRUNC` and byte layout can affect binary edge/overlay interpretation. | TN-04/TN-07, INT-R10. Do represent success/EOF/abend as typed outcomes and model only the evidenced binary status formatting in the adapter. Do not add packed/binary fields to the file layouts or infer a host `TRUNC` setting. | Inject numeric and extended statuses and compare to independently captured formatter output, including a profile-controlled right-byte case. **Confirm on the mainframe** · COBOL compiler / LE owner for deployed `TRUNC`, binary layout and abend linkage. |
| f · Nonnumeric data in a business numeric field | **ABSENT** as an evidenced input behaviour · searched five copybooks, P READ/MOVE/COMPUTE paths and `NUMERIC` checks. P 635–648 tests alphanumeric IO-STATUS only. i01–i07 contain no malformed numeric specimen. | No business-path validation or observed legacy result for malformed numeric data. Extended alphanumeric statuses are handled under q. | D/Z rows, INT-R10. Do reject/quarantine bad digits or zones before sealing with row and offset. Do not coerce blanks or invalid digits to zero. | Synthetic malformed B04 and unsigned-key tests per INT-03. Any host behaviour with invalid numeric bytes needs explicit host evidence before changing the profile. |
| g · Fixed width, padding, MOVE, STRING and reference modification | **PRESENT** · P 167, 178, 194–205, 437, 476–489, 638–645 and CVTRA05Y.cpy 5–11 | Fixed-width MOVE pads short text on the right and would discard excess right-hand text on a narrower alphanumeric receiver. No overlong business MOVE is recorded. The break compares 11-byte operands at their fixed width. STRING copies fixed sizes into ID/description. Status edits use 1-based slices. | D01/T01/T04/T05, INT-R03/R08, TN-04. Do use `DEFAULT   `, fixed-width comparisons and exact layout slices. Do not trim away storage padding or truncate malformed inbound rows to make them fit. | i04-default-group-fallback for padded key and i01-baseline for ID/description bytes. Synthetic leading-zero and status-slice tests. |
| h · EBCDIC, ASCII and collation | **PRESENT** · P 28–32, 63–67, 188–206 and B 16 and INT-MAP B00/D00 and transfer profiles | VSAM walks encoded keys. EBCDIC digits follow letters, unlike ASCII byte order. The fixture text profile and EBCDIC sign flag give off-host evidence, with cp037 a lab profile. | INT-R09. Do sort/seek using unsigned encoded B00 bytes in PostgreSQL `BYTEA` and encode D00 with the same profile. Do not order on decoded SQL text, even with `COLLATE "C"`. | i05-multi-category for recorded order and synthetic cp037 mixed type keys with the same account (e.g. `A1` before `01`, opposite in ASCII). **Confirm on the mainframe** · Host data / VSAM owner for production CCSID, transfer profile and key-order specimens. |
| i · REDEFINES | **PRESENT** · P 125–128, 150–165, 614–624, 635–642 | Timestamp components overlay the same 26 characters. Diagnostics overlay a binary word with two characters and interpret the second storage byte under the selected layout. | TN-02/TN-04, T12–T13. Do format a single timestamp and use a dedicated status formatter. Do not model overlay aliases as mutable business records. | i01 for both timestamp fields plus a synthetic extended-status overlay probe under e. **Confirm on the mainframe** · COBOL compiler / LE owner for the overlay's byte interpretation. |
| j · FILLER and fixed LRECL | **PRESENT** · CVACT01Y.cpy 17, CVACT03Y.cpy 8, CVTRA01Y.cpy 10, CVTRA02Y.cpy 10, CVTRA05Y.cpy 18, P 350–356, 473–500 and J 37–41 | Records are fixed 50/300/350 bytes. Account rewrite preserves every field outside the three changes. Transaction filler has no explicit MOVE. | TN-06, A13/X04/B05/D05/T14. Do retain original filler bytes and use evidenced output filler by profile. Do not trim or substitute zeroes during a round trip. | i01 and i06 byte round-trips and A03/A09/A10-only change test with distinctive filler. **Confirm on the mainframe** · Host / application owner for host framing, unchanged sign bytes and initial T14 bytes. |
| k · OCCURS / 1-based subscripts / OCCURS DEPENDING ON | **ABSENT** · case-insensitive search for `OCCURS` and `DEPENDING ON` in P and the five copybooks found no declarations | No indexed table storage or ODO record. The status slices in g are reference modification. | B00/T01–T14. Do represent fixed records and a stream of groups. Do not introduce a one-based record table. | — |
| l · State across records and use before assignment | **PRESENT** · P 167–173, 188–206, 467, 474–500 and CVTRA05Y.cpy 9, 18 | Account/card and total persist within a group. First group starts the total at zero. The suffix persists across groups. Description's unused tail and output filler rely on initial/retained bytes, with no explicit assignment to those bytes. Account/card are read before use on the success path. | INT-R08/R09, TN-06. Do use per-account state and a per-run ordinal and materialize evidenced unchanged tail bytes. Do not reset the suffix at a break or guess initial bytes. | i05 for repeated use of group data and i01 for suffix across groups and output width. **Confirm on the mainframe** · Host / application owner for record initialization and untouched bytes. |
| m · Control breaks and EOF | **PRESENT** · P 188–222, 325–356 | A different account triggers rewrite of the prior one. Status 10 sets EOF and bypasses the apparently intended final update. Empty input causes no rewrite. | INT-R06/R07/R09, INT-Q02/Q06. Do use lookahead to distinguish a normal break from EOF. Do not flush a final delta on EOF. | i01 for next-account delta and untouched last account, i05 for two transactions and no account delta, and synthetic empty TCATBALF. |
| n · GO TO, PERFORM THRU and fall-through | **ABSENT** on supported execution paths · searched executable P for `GO TO` / `GOTO` / `PERFORM ... THRU` / `THROUGH`. See P 230–234, 628–648 and LE 15–18 | Named single-paragraph PERFORMs return, the entry path ends at GOBACK and diagnostics end at the terminal CEE3ABD call. No execution path relies on falling through into another paragraph. | INT-R09/R10. Do express EOF/abort in the normal Java step flow. Do not preserve paragraph layout as control flow. | — |
| o · Level-88 conditions | **PRESENT** · P 133–135, 325–347 | APPL-AOK is 0 and APPL-EOF is 16 in APPL-RESULT. EOF is an internal outcome of a read, not step RC 16. | INT-R10/R09. Do return a typed end-of-input outcome for TCATBALF status 10. Do not expose APPL-EOF as a failed job exit. | i01 completes with STEP15 RC 0 after EOF. Synthetic empty input also completes. Status 10 at a different operation is a failure. |
| p · Indexed access, AIX, duplicates, START and REWRITE | **PRESENT** · P 28–51, 325–409, 415–446, J 27–36 and XJ 72–77, 90–92. No executable `START file` | Sequential TCATBALF read, random keyed account/rate reads and an alternate account-key XREF read. AIX allows nonunique account keys, while base primary keys are unique. The program rewrites the previously read account. | INT-R06/R09, INT-H03, A01/X01/X03/B00/D00. Do use the sourced first-return card and a patch with the original record preimage. Do not select `MIN(card_number)` or emulate an unused START. | i01 keyed reads and rewrite. Synthetic tests cover a duplicate AIX with supplied first-return evidence, holding intake without that proof, and rejecting a duplicate primary key. **Confirm on the mainframe** · Card operations / VSAM owner for actual first-return card and status for duplicate AIX keys. |
| q · File statuses and abends | **PRESENT** · P 234–460, 500–611, 628–648 and LE 15–18 | 00 succeeds, TCATBALF READ 10 ends input, first DISCGRP READ 23 tries DEFAULT. Other non-00 statuses abort, with operation-specific display and status formatting. CEE3ABD requests U999. | INT-R10/H08, TN-03/04/07/09. Do apply section 6 by file and operation. Do not convert every 23 into a missing-rate fallback. | i04 recoverable 23 and RC 0, plus synthetic missing-account/xref/DEFAULT, abnormal operations and numeric/extended statuses. **Confirm on the mainframe** · COBOL / LE and Operations owners for actual error statuses and U999 disposition. |
| r · JCL PARM, GDG, DISP and RC | **PRESENT** · J 22, 27–41 and P 176–178, 230–232, 476–480, 628–632 and LE 15–18 | STEP15 passes ten bytes, inputs are SHR and output is SYSTRAN(+1), NEW/CATLG/DELETE, RECFM=F LRECL=350. Normal RC 0 is recorded. U999 ends the host step, with RC 12 in the lab stub. | INT-H02/H06, INT-R08/R10. Do check the one verified absolute generation and publish only after receipts. Do not treat a partial write as a cataloged generation or restart automatically. | i01 manifest/RC for STEP15 and recorded G0001V00, and synthetic normal/abnormal handoff plus withheld-success tests. **Confirm on the mainframe** · Scheduler / Operations and VSAM owners for live PARM, catalog/disposition and downstream condition. |
| s · DISPLAY and parity SYSOUT | **PRESENT** · P 181, 193, 230, 281, 415–420, 628–648 | Start/end banners, full 50-byte balances read, fallback/failure messages and abend text are output in order. The status/operation text can be misleading but is observable. | TN-01/TN-04/TN-09, INT-R03/R10. Do reproduce exact ordered SYSOUT in the restricted parity artifact. Do not put raw account records or IDs in application logs. | i04 full SYSOUT, including both fallback messages, and i03 verifies that the skipped balance is still displayed. Synthetic DISCGRP open failure preserves `DALY REJECTS FILE`. |
| t · Business date/PARM versus wall clock | **PRESENT** · J 22 and P 176–178, 476–480, 496–498, 614 | PARM provides only the fixed transaction-ID prefix. CBACT04C leaves its format and length unchecked. The timestamps come from another input, the wall clock. | INT-H02/Q05, T01/T12–T13. Do bind opaque cycle text separately from the injected fixture clock. Do not derive the prefix from today or timestamps from PARM. | i01 shows ID prefix 2022071800 and timestamp date 2022-07-05. **Confirm on the mainframe** · Scheduler / application owner for live substitution and date domain. |
| u · CURRENT-DATE and hundredths | **PRESENT** · P 140–165, 496–498, 613–626 | One reading per emitted row supplies both timestamps, with two hundredth digits followed by four literal zeros in a 26-character field. The CURRENT-DATE offset is discarded. | INT-Q05, TN-02, T12/T13. Do render `YYYY-MM-DD-HH.MM.SS.hh0000` from an injected local clock (here `hh` is hundredths). Do not round to milliseconds, sample twice, or use a zone suffix in output. | i01 frozen timestamps plus a synthetic advancing clock with nonzero hundredths. **Confirm on the mainframe** · Application / z/OS runtime owner for region timezone, DST and clock behaviour. |
| v · Two-digit years and Julian dates | **ABSENT** · searched P and its copybooks for `JULIAN`, `YYDDD`, `DAY-OF-INTEGER`, `INTEGER-OF-DAY`, `DATE-OF-INTEGER` and `INTEGER-OF-DATE`. Also reviewed J 22 and P 140–178, 613–624 | Timestamp year is four characters. PARM and account dates are opaque to this job. | A06–A08/T01/T12–T13. Do preserve the field text. Do not add a century pivot or Julian conversion. | — |
| w · No-op and unfiltered paths | **PRESENT** · P 194–217, 234–236, 372–391, 482–483, 518–520 | Fee paragraph exits immediately, account status/expiry are ignored, TCATBALF is input-only, and no transaction category table is read. An account with no balance is never looked up. | INT-H01/H04/H09, INT-Q06, TN-08. Do compute only from encountered balances and rates. Do not assess fees, filter accounts, reset categories or scan accounts to fill gaps. | i01 shows interest-only emissions. Add synthetic inactive/expired and no-balance accounts with sentinels and assert input immutability. |
<!-- END COBOL BEHAVIOURS -->

### Interest review checklist

Ticket sessions copy each line into the review evidence and record **PASS** or **FAIL**, with a diff/test reference. For a standard outside the ticket's scope, PASS requires the diff to leave that behaviour or contract untouched. Source the checks from the rules and rows cited above.

- **S01** · Does the PR trace the assigned ticket and rules through the design/map to the source ranges used?
- **S02** · Do assertions use recorded legacy outputs, and are conflicting claims cited for the register owner?
- **S03** · Are operations and typed outcomes rule-driven, without paragraph translation, procedural flags or COBOL control-flow emulation?
- **S04** · Are business records immutable, with COBOL names at their bindings and original bytes protected?
- **S05** · Do compute, package and handoff respect the Spring Batch and PostgreSQL boundaries of the ticket?
- **S06** · Are dependencies constructor-injected and mutable state scoped to the run or group?
- **S07** · Are scale, receiver width and rounding exact, with no floating-point types?
- **S08** · Are encoding, sign and framing confined to the boundary and verified profile?
- **S09** · Are the opaque PARM and local timestamp source injected independently?
- **S10** · Does each touched quirk/OPEN decision have one named implementation point and an ID in code and test name?
- **S11** · Are discrepancies routed to owners, decisions left OPEN and unproven host outcomes held?
- **S12** · Does each touched status path produce the specified outcome, exit and diagnostic?
- **S13** · Do private restart and host hold/reconciliation paths preserve committed state and prevent duplicate effects?
- **S14** · Are ordinary logs masked and parity SYSOUT restricted?
- **S15** · Do applicable rule-named tests cover their listed fixtures and targeted gaps with independent expectations?
- **S16** · Is required PostgreSQL 15, field/RC/SYSOUT and byte evidence present at the ticket's scope?
- **S17** · Are legacy source, fixtures, goldens and comparator untouched?
- **S18** · Are frozen interfaces untouched and new dependencies justified in the PR?
- **S19** · Is all supplied data synthetic?
- **a** · Does the stored cent value truncate toward zero before summing, including positive and negative fractions?
- **b** · Do receiver widths and suffix wrap retain low digits without dropping emitted rows?
- **c** · Is intermediate product precision preserved and host arithmetic evidence still called out?
- **d** · Are signed zoned values handled at the boundary while unsigned fields preserve digit width?
- **e** · Is COMP/BINARY isolated to internal outcomes and diagnostics, with host options unresolved pending evidence?
- **g** · Do PAD/MOVE width, fixed comparison, STRING fields and status slices match the legacy layout?
- **h** · Does ordering/seek use encoded unsigned key bytes and distinguish the lab profile from host evidence?
- **i** · Are both overlays accounted for by a timestamp formatter and a profile-aware status adapter?
- **j** · Are all LRECLs and filler/untouched bytes preserved in byte tests?
- **l** · Does state persist within its intended scope without reliance on unassigned bytes?
  For newly emitted transactions in the lab fixture profile, explicitly fill TRAN-RECORD
  FILLER and the unused TRAN-DESC tail with spaces, as `InterestTransaction` does in INT-01.
  This documents the pilot's choice, not the legacy host bytes; host initialization remains
  unconfirmed (§4 item l).
- **m** · Are normal breaks, EOF and empty input covered, including the withheld final delta?
- **o** · Are success and EOF typed outcomes with no APPL-RESULT leakage into RC?
- **p** · Do keyed reads, nonunique AIX selection and guarded rewrite use the recorded key and preimage contracts?
- **q** · Do EOF, fallback and terminal status/abend paths match the file and operation-specific rules?
- **r** · Do PARM, fixed GDG output, DISP intent and step RC drive the correct handoff and hold paths?
- **s** · Does restricted SYSOUT match ordered DISPLAY including skipped inputs and fallback lines?
- **t** · Do IDs and timestamps use their distinct verified inputs?
- **u** · Do both timestamp fields share one sample, truncate to hundredths and append four zeros?
- **w** · Are fee, filter, blanket reset and category-clear paths absent from the target calculation?

## Historical build steps

The wording and results in this section are the original interest material. Use the combined replay inputs above for the Step 5c proof.

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

### Build 1

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

### Build 2

2. Install Java 21 if `java -version` is not 21 (the image ships 17):
   ```sh
   sudo apt-get install -y -q openjdk-21-jdk-headless
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
   mvn -v
   ```
   Success: `Setting up openjdk-21-jdk-headless:amd64 (21.0.13.1+2-1~22.04.1) ...`, then
   `Apache Maven 3.9.9` and `Java version: 21.0.13.1`. `JAVA_HOME` is per shell: export it in
   every new shell before `mvn`.

### Build 3

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

### Build 4

4. Build and run all tests (unit tests, then PostgreSQL 15 integration tests in Testcontainers;
   Docker must be running, and the mirrored `postgres:15-alpine` tag of step 3 must exist:
   Testcontainers pulls it too and hits the same Docker Hub 429; and `JAVA_HOME` must be the
   Java 21 of step 2 in this shell). In Bash, keep the full output for the evidence (use your ticket
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

### Build 5

5. Run the parity driver's own unit test:
   ```sh
   python3 -m unittest discover -s java/interest/interest-batch/src/test/resources/pilot/parity -v
   ```
   Success: `Ran 1 test` and `OK`.

### Build 6

6. Check that the code diff stays inside the ticket's Scope and touches nothing frozen
   (standards §8, S17/S18). `<base>` is the base branch of step 1; the pilot's was
   `demo/04a-standards`: `git diff --stat origin/<base> -- java/interest`. Every listed path must
   match the ticket's Scope or be under `java/interest/evidence/<ticket>/`. `target/` is build
   output and is not committed.

### Build 7

7. Prove the ticket with [interest-ticket-verify](../interest-ticket-verify/SKILL.md).

## Historical verify steps

The wording and results in this section are the original interest material. Use the combined replay inputs above for the Step 5c proof.

Every command below was run from the repository root on a clean checkout after
[interest-ticket-build](../interest-ticket-build/SKILL.md) and succeeded; the quoted line is
the output that shows it. Which behaviour a ticket must reproduce, and which tests prove it,
is in [interest-conversion-standards](../interest-conversion-standards/SKILL.md) §5, §7 and
§9; field-level parity in general is [carddemo-parity](../carddemo-parity/SKILL.md).

`<ticket>` below is the ticket file name without `.md` (`01-pilot`, `02-schema`,
...). Each ticket writes its evidence only under `java/interest/evidence/<ticket>/` and its
runtime output only under `work/<ticket>/`, so tickets built in parallel do not overwrite each
other. The pilot's own evidence stays under
`java/interest/interest-batch/src/test/resources/pilot/evidence/`.

### Verify 1

1. In Bash, enable pipeline error propagation, make the evidence directory and start a fresh
   report (`--report` appends; a rerun into an old report repeats every heading):
   ```sh
   set -o pipefail
   T=01-pilot
   mkdir -p java/interest/evidence/$T
   rm -f java/interest/evidence/$T/compare-report.md
   ```

### Verify 2

2. Turn each case's Java job output into comparator files. The driver runs the real legacy
   setup jobs (DEFGDGB, ACCTFILE, XREFFILE, TCATBALF, DISCGRP), unloads their KSDS datasets for
   Java intake, runs the jar in place of INTCALC STEP15 against compose PostgreSQL, and writes
   `datasets/`, `sysout/` and `rc.json` (setup RCs as observed plus the Java exit code) under
   `--out`. It must run inside `estate`, one case at a time (it recreates the shared
   `datasets/`), and `--out` must be a case directory below `work/`:
   ```sh
   P=java/interest/interest-batch/src/test/resources/pilot
   for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
            i05-multi-category i06-negative-balance i07-nontie-rounding; do
     docker compose exec -T \
       -e INTEREST_PILOT_JDBC_URL=jdbc:postgresql://db:5432/carddemo \
       -e INTEREST_PILOT_DB_USER=carddemo -e INTEREST_PILOT_DB_PASSWORD=carddemo \
       estate python3 "$P/parity/run_case.py" $c --out work/$T/$c || exit "$?"
   done 2>&1 | tee java/interest/evidence/$T/e2e-run.log
   ```
   Success, once per case: `interest/<case>: Java STEP15 RC=0, outputs in /estate/work/01-pilot/<case>`.
   The driver runs the jar's Start-Class (the pilot). A ticket that adds a new entry point
   under the frozen `pom.xml` passes `--java <wrapper>`: a script that runs the jar with
   `-cp <jar> -Dloader.main=<main class> org.springframework.boot.loader.launch.PropertiesLauncher`
   (INT-06's is `java/interest/interest-batch/scripts/parity/intcalc_java.py`, which runs the
   joined job `com.carddemo.interest.batch.InterestBatchApplication`).
   The cases are the directories under `fixtures/interest/`; the ticket's **Verification**
   line names the ones that gate it.

### Verify 3

3. Compare each case field by field against the golden files (host Python, comparator
   unchanged):
   ```sh
   for c in i01-baseline i02-tie-half-cent i03-zero-rate-skipped i04-default-group-fallback \
            i05-multi-category i06-negative-balance i07-nontie-rounding; do
     python3 tools/parity/compare.py interest $c --actual work/$T/$c --label java \
       --report java/interest/evidence/$T/compare-report.md || exit "$?"
   done | tee java/interest/evidence/$T/compare-stdout.txt
   ```
   Success, each case with exit 0:
   ```text
   interest/i01-baseline [java]: PASS (4 records, 50 fields, 0 differences)
   interest/i02-tie-half-cent [java]: PASS (4 records, 50 fields, 0 differences)
   interest/i03-zero-rate-skipped [java]: PASS (2 records, 25 fields, 0 differences)
   interest/i04-default-group-fallback [java]: PASS (2 records, 25 fields, 0 differences)
   interest/i05-multi-category [java]: PASS (3 records, 38 fields, 0 differences)
   interest/i06-negative-balance [java]: PASS (2 records, 25 fields, 0 differences)
   interest/i07-nontie-rounding [java]: PASS (4 records, 50 fields, 0 differences)
   ```
   `grep -c '^## ' java/interest/evidence/$T/compare-report.md` prints `7`.
   A nonzero case exits the loop immediately and makes the pipeline nonzero even when
   `tee` succeeds. Stop on a nonzero exit; do not treat a partial log as parity evidence.

### Verify 4

4. Read a difference. A failing case prints
   `interest/<case> [java]: FAIL (<records> records, <fields> fields, <n> differences)` and
   exits 1. Its section in `compare-report.md` is headed
   `## interest / <case> — java: **FAIL (<n> difference)**` and has a table
   `| <file> | <field> | expected | actual |` (decoded copybook field, or `(text)` with a line
   diff for SYSOUT and `rc.json`) followed by `Fields differing: ...`. Read it as: the golden
   (legacy) value is right, the Java value is wrong, unless the field is a documented quirk
   (standards §5) the candidate has not reproduced. Fix the Java code, rerun steps 2-3 for that
   case. Never edit `fixtures/`, `tools/parity` or `app/` to make it pass (AGENTS.md). A
   difference in `rc.json` alone means the actual file does not list every INTCALC step code
   the golden lists; see [interest-pitfalls](../interest-pitfalls/SKILL.md).

### Verify 5

5. Check the PostgreSQL rows of the ticket case against the case README. Take the first run's
   id from the driver log and run the ticket's SQL assertions (the pilot's are
   `java/interest/evidence/01-pilot/i01-sql.sql`; every assertion selects one boolean):
   ```sh
   run_id=$(grep -m1 -o 'event=run-completed run=[^ ]*' java/interest/evidence/$T/e2e-run.log | cut -d= -f3)
   docker compose exec -T db psql -U carddemo -d carddemo -v ON_ERROR_STOP=1 -v run_id="$run_id" -f - \
     < java/interest/evidence/$T/i01-sql.sql | tee java/interest/evidence/$T/i01-sql-output.txt
   ```
   Success: exit 0 and ` t` under `transactions_pass`, `delta_pass` and
   `final_account_unchanged_pass`. The run id differs on every run.
   A ticket whose SQL assertions target the `interest` / `interest_batch` schemas rather than
   the pilot's (INT-02, INT-05) may assert them inside its own PostgreSQL 15 IT
   (Testcontainers) and keep the printed assertion output as this step's evidence.

### Verify 6

6. Write `java/interest/evidence/<ticket>/parity-report.md` in the pilot's format (its example:
   `java/interest/interest-batch/src/test/resources/pilot/evidence/parity-report.md`):
   - title line `# <ticket id> – per-case parity report (open this first)`, then the code
     revision, Java / Spring / PostgreSQL versions and the legacy baseline command and result;
   - the exact commands of steps 2-3;
   - `## Result per case`: one row per case,
     `| Case | Ticket case | compare.py | Datasets (records / decoded fields) | SYSOUT STEP15 | rc.json |`,
     e.g. `| i01-baseline | yes | **PASS** | 4 / 50, 0 differences | identical | identical |`;
   - the SQL result of step 5 with the run id, and the Maven totals from interest-ticket-build
     step 4;
   - a list of the raw files beside it: `test-output.txt`, `e2e-run.log`,
     `compare-stdout.txt`, `compare-report.md`, the SQL script and its output.

### Verify 7

7. For a new ticket, commit `java/interest/evidence/<ticket>/`. For the pilot replay, commit
   the new `java/interest/evidence/01-pilot/parity-report.md` only; leave the pilot's existing
   evidence files unchanged. `work/` is runtime output and is not committed.

## Historical pitfalls

The wording and results in this section are the original interest material. Use the combined replay inputs above for the Step 5c proof.

Only failures the pilot hit (its report:
`java/interest/interest-batch/src/test/resources/pilot/evidence/PILOT-REPORT.md`, section
"Historical direct-jar method and failures", plus its review rounds). Each was reproduced in
Step 5a with the quoted output unless marked **reported by the pilot, not reproduced**. Where
the fix is a standard, the section of
[interest-conversion-standards](../interest-conversion-standards/SKILL.md) is named and is the
whole fix.

### Pitfall 1

1. **Maven stops in `validate` on Java 17.**
   - Symptom: `RequireJavaVersion failed with message: ... Detected JDK version 17.0.20-1 ... is not in the allowed range [21,22).`
   - Cause: the image ships Java 17; the module requires 21.
   - Fix: interest-ticket-build step 2. Then
     `mvn -B -f java/interest/interest-batch/pom.xml validate` prints `RequireJavaVersion passed` and `BUILD SUCCESS`.

### Pitfall 2

2. **Testcontainers cannot find Docker.**
   - Symptom: `Could not find a valid Docker environment`, with
     `client version 1.32 is too old. Minimum supported API version is 1.40`; ITs end
     `Errors: 2` and `BUILD FAILURE` (reproduced with `-Dtestcontainers.version=1.20.3`).
   - Cause: Testcontainers 1.20.x speaks an API older than the Docker daemon here (1.56) accepts.
   - Fix: keep `testcontainers.version` 1.21.4 in `pom.xml` (no override); the log then shows
     `Testcontainers version: 1.21.4` and `BUILD SUCCESS`.

### Pitfall 3

3. **Every IT after the first fails with "Could not open JDBC Connection"** (30 s timeouts) -
   reported by the pilot, not reproduced.
   - Cause: a `@Container` static field in an abstract base class is stopped after the first IT
     class while the cached Spring context keeps its URL.
   - Fix: one singleton container started in a static block, as
     `src/test/java/com/carddemo/interest/pilot/PilotPostgresTestSupport.java` does.

### Pitfall 4

4. **Docker Hub HTTP 429 on image pulls** (`make up`, Testcontainers) - reported by the pilot,
   not reproduced.
   - Cause: anonymous Docker Hub rate limit.
   - Fix (pilot): build the estate with `ESTATE_BASE_IMAGE=mirror.gcr.io/library/ubuntu:22.04`,
     and pull other images from `mirror.gcr.io` and `docker tag` them to the Docker Hub name.

### Pitfall 5

5. **`mvn -o` fails resolving the enforcer plugin** - reported by the pilot, not reproduced.
   - Cause: the plugin is not in the local repository yet.
   - Fix: run the build online once (interest-ticket-build step 4).

### Pitfall 6

6. **A test expects the wrong amount from a zoned field.**
   - Symptom: test data read `00000012345{` as `123.45`.
   - Cause: the overpunch byte is the last digit (`{` = +0), not a sign to drop:
     `python3 -c 'from tools.parity.copybook import _decode_display; print(_decode_display(b"00000012345{", 2, True, "ascii"))'`
     prints `1234.50`.
   - Fix: standards §3 and §4.

### Pitfall 7

7. **compare.py fails only on `rc.json`.**
   - Symptom: `interest/i01-baseline [java]: FAIL (4 records, 50 fields, 1 differences)`; the
     report row is `| rc.json | (text) | ...` with golden steps `STEP05` to `STEP30` against
     the actual `{"INTCALC":{"maxrc":0,"steps":{"STEP15":0}}}`.
   - Cause: the comparator also checks `rc.json` and SYSOUT, and the golden `rc.json` lists the
     setup jobs' step codes under INTCALC (recorder finding 1 in the pilot report, OPEN). Running
     the jar alone yields only STEP15.
   - Fix: run each case through the driver (interest-ticket-verify step 2), which executes the
     real setup jobs and uses their observed codes; standards §7 (S16). Never edit the golden.

### Pitfall 8

8. **A Spring Batch flow with custom exit statuses ends FAILED although every step succeeded**
   - reported by INT-06 (step 5b, g4-gate).
   - Symptom: the job's last step returns exit status `0` and the job ends with an
     unmatched-transition failure.
   - Cause: a flow whose steps set custom numeric exit statuses (`0`, `12`) has no transition
     for those statuses unless every step declares them.
   - Fix: give every step explicit transitions, ending with `on("*")`, as
     `batch/InterestJobConfig.java` does.

### Pitfall 9

9. **Review findings on the pilot** (PILOT-REPORT, "Review fixes" and "Review round 2"):
    chunked commits that could leave a partial run (fix: standards §2, S05), PARM not checked as
    ten bytes (§3, S09), no log event for the DISCGRP fallback (§6, S14), locale-dependent digits
    and lost filler bytes (§3), stale artifacts after a failed rerun into a reused directory
    (§6). Reported by the pilot, fixed in its code, not reproduced.
