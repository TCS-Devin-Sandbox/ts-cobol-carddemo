---
name: carddemo-conversion-standards
description: Apply the proposed CardDemo batch conversion standards to rules, data mapping, Java and review.
---

# CardDemo conversion standards

**PROPOSED · Owner: Target architect.** Read these standards and the stream annex before changing anything and fill in section 9 in the PR evidence. Later tickets cite this skill and submit proposed changes for target architect review. Edits to the skill sit outside ticket scope. Use the annex to resolve each standard's source lines, rule IDs, examples and host-evidence questions.

## 1. What to convert from, in this order

1. **S01 · Scope, then shape, then source.** Read the assigned ticket, rules and OPEN decisions for the what and why. Read the stream architecture and data map for the target. Before implementing a rule, read its cited COBOL lines and relevant copybook. Trace the rule to executable source before implementing behaviour described in a JCL comment.
2. **S02 · Recorded legacy output decides parity.** Compare with the committed goldens under `fixtures/<stream>/` using [carddemo-parity](../carddemo-parity/SKILL.md). When prose and COBOL disagree, reproduce the observed code behaviour and send the ID, source lines, affected fields, fixture evidence and discrepancy to the decision-register owner. Hold unresolved live behaviour for evidence.

## 2. Idiomatic Java, not translated COBOL

- **S03 · Model rules as business operations.** Use small rule-driven functions and typed results for lookup, skip, group completion and failure. **Do not** create a method per COBOL paragraph, emulate GO TO or PERFORM THRU, mirror WORKING-STORAGE in a class, or use flags/switches in place of a result or type.
- **S04 · Use immutable business records.** Map the copybooks to records named for the business data. Name each COBOL field at its data-map/codec binding once. Keep raw bytes immutable when retained. Keep mutable copybook buffers out of processing.
- **S05 · Follow the stream's Spring Batch boundaries.** Use a reader for ordered input from the sealed snapshot, keyed lookups and processing for rule outcomes, and a writer for private results, eligible deltas and checkpoint in one PostgreSQL transaction per group where the stream defines a group. A ticket that excludes restart may commit the whole run as one transaction so a failure commits nothing. Restart tickets commit per group. Where a group break requires lookahead, preserve the legacy order between evaluating the next group's lookups and completing the previous group. Follow the stream architecture and ticket scope. **Do** keep external handoff outside compute and retrying chunks. **Do not** checkpoint by Spring's offset independently or publish from an item writer.
- **S06 · Inject collaborators through constructors.** Keep mutable execution state per run or group. **Do not** share a mutable static counter, clock, cached record or record buffer.

## 3. Numbers and data

- **S07 · Assign exact decimals at each legacy receiver.** Use decimal literals and `BigDecimal` throughout for money and rates. Multiply and add exactly with no intermediate rounding when the source expression has that behaviour. State exactness or a rounding mode at every arithmetic/assignment step (use `UNNECESSARY` for values already exact at the receiver's scale). Apply the receiver's high-digit limit on each COBOL assignment/addition, preserving sign in signed receivers. Decode original values without rounding. **Do not** use `double`, `float`, an intermediate precision cap, half-even, or PostgreSQL coercion for arithmetic in place of the evidenced receiver behaviour.
- **S08 · Decode and encode only at the boundary.** Use the stream's data map and verified transfer profile for EBCDIC, DISPLAY digits and zoned overpunch. Keep filler/raw bytes and encoded keys as mapped. Use only the representations evidenced by the layouts. Isolate any binary diagnostic overlay in the status adapter. Pass typed decoded values to the application.
- **S09 · Inject separate cycle and wall-clock inputs.** Bind a cycle PARM evidenced as opaque text at its exact byte-compatible width and inject the clock and time zone independently. Freeze the fixture clock and reproduce the legacy sampling and output format. Keep opaque record dates as fixed text. Hold the run when a required PARM violates its verified profile. **Do not** parse an opaque PARM into a timestamp or call the system clock from the job.

## 4. COBOL behaviours with no Java equivalent

Use each row to check presence, absence or missing evidence in the stream. Attach the source ranges, relevant copybooks, tests and host confirmation gaps in the annex. Synthetic probes use independent expected values and are labelled when the recorded cases do not exercise the path.

| ID · Behaviour | Shared guidance and evidence to retain |
|---|---|
| a · Decimal truncation | When the receiver omits `ROUNDED` and the assignment discards fractional digits, use truncation toward zero at that receiver before accumulation. Test ties, non-ties and negative remainders where applicable. |
| b · Silent loss of high digits | Evidence receiver width, sign and size-error handling. Where DISPLAY retains low digits, apply signed unscaled remainder at each receiver's precision. Verify emitted row identity separately from any wrapped field. Confirm deployed compiler options and boundary vectors with the compiler/application owner. |
| c · Intermediate `COMPUTE` precision | Preserve the exact expression until its documented assignment/rounding boundary. Do not narrow the product to the destination PIC. Confirm deployed Enterprise COBOL `ARITH` and boundary outputs with the compiler/application owner. |
| d · Unsigned fields and zoned overpunch | Decode signed amounts and preserve sign at the boundary. Do not accept a signed digit in an unsigned key or send an overpunch to the processor. Test byte round-trips and confirm deployed sign zones, signed-zero handling and `NUMPROC`. |
| e · Packed, binary and compile-option truncation | Implement only evidenced storage usages. Represent success/EOF/abend as typed outcomes and isolate evidenced binary diagnostic formatting. Confirm deployed `TRUNC`, binary layout and abend linkage with the compiler/LE owner. |
| f · Nonnumeric data in a business numeric field | Do reject/quarantine bad digits or zones before sealing with row and offset. Do not coerce blanks or invalid digits to zero. Use a labelled synthetic probe when fixtures lack one. Require explicit host evidence before changing the profile. |
| g · Fixed width, padding, MOVE, STRING and reference modification | Preserve fixed comparison, MOVE width, padding, STRING field boundaries and exact layout slices. Do not trim away storage padding or truncate malformed inbound rows to make them fit. |
| h · EBCDIC, ASCII and collation | Where VSAM walks encoded keys, sort/seek by the unsigned encoded bytes under the verified profile. Do not order on decoded SQL text, even with `COLLATE "C"`. Confirm production CCSID, transfer profile and key-order specimens with the host data/VSAM owner. |
| i · REDEFINES | Reproduce the evidenced interpretations at a formatter or adapter boundary. Do not model overlay aliases as mutable business records. Verify the byte interpretation of diagnostic overlays with the compiler/LE owner. |
| j · FILLER and fixed LRECL | Do retain original filler bytes and use evidenced output filler by profile. Do not trim or substitute zeroes during a round trip. Test fixed widths and untouched bytes. Confirm host framing, sign bytes and initialization with the host/application owner. |
| k · OCCURS / 1-based subscripts / OCCURS DEPENDING ON | Check declarations for real indexed or variable table storage. Document a negative search where absent. Use the actual record layout without introducing a table absent from source. |
| l · State across records and use before assignment | Scope state by its evidenced lifetime, including across rows or groups. Materialize evidenced untouched tails. Confirm host initialization and untouched bytes with the host/application owner instead of guessing. |
| m · Control breaks and EOF | Trace normal breaks, EOF and empty input separately. Use lookahead where the next input controls disposition. Implement the recorded outcome of each boundary rather than assuming a final flush. |
| n · GO TO, PERFORM THRU and fall-through | Trace executable paths, returns and termination. Express EOF/abort in the normal Java step flow. Do not preserve paragraph layout as control flow. |
| o · Level-88 conditions | Translate internal condition values to typed outcomes. Keep an internal EOF value separate from the job exit contract. |
| p · Indexed access, AIX, duplicates, START and REWRITE | Reproduce evidenced key order, lookup and rewrite semantics. Where a nonunique AIX returns one result, use sourced first-return evidence and hold intake when that proof is required but absent. Use a guarded patch against the original preimage. Confirm duplicate-key selection and status on the host. |
| q · File statuses and abends | Apply section 6 by file and operation. Probe normal, search miss, extended status and terminal paths as applicable. Confirm actual statuses and abend disposition with compiler/LE and Operations. |
| r · JCL PARM, GDG, DISP and RC | Trace parameter bytes, generation references, framing, normal/abnormal disposition and step RC. For a handoff, verify the absolute generation and wait for receipts before release. Confirm host substitution, catalog/disposition and downstream conditions with scheduler/Operations and VSAM owners. |
| s · DISPLAY and parity SYSOUT | Do reproduce exact ordered SYSOUT in the restricted parity artifact. Do not put raw account records or IDs in application logs. |
| t · Business date/PARM versus wall clock | Evidence each input's role. Bind opaque cycle inputs separately from an injected fixture clock where both exist. Confirm live substitution and date domain with scheduler/application owners. |
| u · CURRENT-DATE and hundredths | Evidence sample frequency, precision, formatting and zone treatment. Test against a fixture clock and a labelled advancing-clock probe when relevant. Confirm region zone, DST and clock behaviour with application/runtime owners. |
| v · Two-digit years and Julian dates | Check declarations and date-conversion paths. Preserve opaque field text and apply a century pivot or Julian conversion only with source evidence. |

## 5. Quirks and decisions

- **S10 · Keep one named implementation point and a rule-named test for each QUIRK or OPEN decision.** Cite the full ID in code and in the test name (underscores may replace hyphens). Preserve legacy option A where evidenced, and trace every emitted, skipped or held result to a rule or technical note. No silent fixes or tidying that changes output.
- **S11 · Report source and wording gaps.** Cite the source and recorded post-images and route the discrepancy to the appropriate decision owner. Keep decisions OPEN. Treat uncertain surviving side effects, scheduling or resubmission as host questions for Operations / application. Exercise the contained failure boundary in the stream architecture and hold unverified effects.

## 6. Errors and operations

- **S12 · Map statuses by operation, then map exit and diagnostics.** Use the annex's per-operation status matrix and the ticket's job contract. Preserve successful completion and normal EOF as distinct typed outcomes. Preserve recoverable lookup outcomes only for the operations where evidenced. Map terminal I/O failures to typed outcomes with DD, operation and status, plus the exact restricted diagnostic and evidenced exit. Use a separate FAILED or HELD reason for target validation, infrastructure and handoff failures. Do not invent a COBOL file status for them.
- **S13 · Preserve private restart and hold uncertain publication.** Commit the checkpoint with each group's rows and delta. Use the same sealed snapshot, revision and parameters. Roll back the entire uncommitted group and keep already committed rows/clocks. Resume failed private work only through the stream's explicit recovery path. Rebuild a partial intake on a new attempt or verify the exact manifest before resuming. Recreate packages from committed stage data by run/hash. Treat unproven host recovery as manual and fenced. Reconcile journaled images and any absolute GDS/digest after an unknown write before a retry or downstream release.
- **S14 · Keep diagnostic evidence out of ordinary logs.** Use run, phase, DD, operation, status, reason and counts. Write exact restricted SYSOUT in source order. Preserve operation-specific text, status formatting under the evidenced profile and the exact lab abend line when called. **Do not** log card/account identifiers, raw records, balances or unsanitized exception messages.

## 7. Tests

- **S15 · Give every rule an independent assertion.** Across the stream require at least one named test per rule/quirk/host-question ID and cover applicable technical notes. In each ticket cover its rules, including the cases its rule lists and synthetic gap cases for rules without fixtures. Do not assert against a copy of the candidate's output.
- **S16 · Run PostgreSQL 15 integration and compare candidate evidence.** Use a container for schema, ordering, transactions and restart tests. Follow [carddemo-ticket-build](../carddemo-ticket-build/SKILL.md) for the Maven verification command. Follow ticket verification through the combined all-case gate. Use the unchanged comparator against committed dataset fields, SYSOUT and RC, with byte/filler/framing tests separately. Produce the RC evidence by running the real setup jobs and substituting only the converted step or steps, whose RC comes from the candidate's process exit code. Check native persisted order, signed sums, post-images, rollback and no replay of committed work where in scope. **Do not** substitute H2 or call matching rounded totals parity.

## 8. Guardrails

- **S17 · Freeze the legacy and evidence inputs.** Keep `app/`, `fixtures/`, goldens and `tools/parity/` untouched. Add missing specimens in ticket-owned test resources. Do not re-record or adjust goldens to make candidate results pass.
- **S18 · Freeze shared interfaces and justify dependencies.** Respect the ticket scope and established architecture and data-map contracts. State why any new dependency is needed in the PR. Preserve shared schemas, tables and frozen codec contracts from consuming tickets.
- **S19 · Synthetic data only.** Use only synthetic data and dataset contents in repo inputs, tests, outputs and PR attachments. **Do not** copy a real host record, hash derived from a real account ID, or production SYSOUT into a PR.

## 9. Review checklist

Ticket sessions copy each line into the review evidence and record **PASS** or **FAIL**, with a diff/test reference. For a standard outside the ticket's scope, PASS requires the diff to leave that behaviour or contract untouched. Source the checks from the rules and rows cited above. Use the annex to add stream-specific checklist details.

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
- **a** · Does each truncating receiver cut fractional digits toward zero at the evidenced assignment?
- **b** · Do receiver widths retain evidenced low digits with wrapped fields distinct from row identity?
- **c** · Is intermediate product precision preserved and host arithmetic evidence still called out?
- **d** · Are signed zoned values handled at the boundary while unsigned fields preserve digit width?
- **e** · Are packed/binary values limited to evidenced layouts and diagnostics, with host options unresolved pending evidence?
- **g** · Do PAD/MOVE width, fixed comparison, STRING fields and status slices match the legacy layout?
- **h** · Does ordering/seek use encoded unsigned key bytes and distinguish the lab profile from host evidence?
- **i** · Are evidenced overlays accounted for at formatter and adapter boundaries?
- **j** · Are all LRECLs and filler/untouched bytes preserved in byte tests?
- **l** · Does state persist within its intended scope without reliance on unassigned bytes?
- **m** · Are normal breaks, EOF and empty input covered with the recorded final disposition?
- **o** · Are success and EOF typed outcomes with no internal outcome leakage into RC?
- **p** · Do keyed reads, nonunique AIX selection and guarded rewrite use the recorded key and preimage contracts?
- **q** · Do EOF, recoverable and terminal status/abend paths match the file and operation-specific rules?
- **r** · Do PARM, GDG output, DISP intent and step RC drive the correct handoff and hold paths?
- **s** · Does restricted SYSOUT match ordered DISPLAY, including evidenced skip and fallback lines?
- **t** · Do business parameters and timestamps use their distinct verified inputs?
- **u** · Does timestamp sampling and formatting match the observed source and fixtures?
