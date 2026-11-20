# INT-04 conversion review

Source below: `.agents/skills/interest-conversion-standards/SKILL.md` §9, copied verbatim,
including the four continuation lines under l. Each copied check/continuation line is
followed by its result/reference. Test names refer to the new `calc` / `job` test packages
unless qualified. File names refer to the new `calc`, `job`, `intake` production packages.
Out-of-scope PASS means that the frozen contract is unchanged, not host verification.
See `parity-report.md` for the lab/synthetic status evidence and external owner/evidence gaps.

38 criteria: **38 PASS / 0 FAIL**. Four l continuation annotations: **4 PASS / 0 FAIL**.
Total result annotations: **42 PASS / 0 FAIL**.

## 9. Review checklist

Ticket sessions copy each line into the review evidence and record **PASS** or **FAIL**, with a diff/test reference. For a standard outside the ticket's scope, PASS requires the diff to leave that behaviour or contract untouched. Source the checks from the rules and rows cited above.

- **S01** · Does the PR trace the assigned ticket and rules through the design/map to the source ranges used?
  **PASS** — `parity-report.md` traceability table; rule-cited `InterestRules`, `GroupCalculation`, `SnapshotIntake`; ticket/design/map/rules remain frozen.
- **S02** · Do assertions use recorded legacy outputs, and are conflicting claims cited for the register owner?
  **PASS** — `InterestCloseIT.INT_R01_INT_R02_INT_R03_INT_R04_INT_R05_INT_R06_INT_R07_INT_R08_INT_R09_INT_R10_allSevenRecordedCases`; `compare-report.md`; final-account README/register conflict and owners in `parity-report.md`.
- **S03** · Are operations and typed outcomes rule-driven, without paragraph translation, procedural flags or COBOL control-flow emulation?
  **PASS** — `AccountGroups.Group`, `GroupCalculation.Outcome`, `MissingLookup`, `SnapshotIntake.Result`, `CloseRunService.Result`; no paragraph-shaped target flow.
- **S04** · Are business records immutable, with COBOL names at their bindings and original bytes protected?
  **PASS** — frozen codec records/defensive `Bytes`; `Group`/`Outcome` immutable list copies; distinctive-filler IT and snapshot tamper refusal.
- **S05** · Do compute, package and handoff respect the Spring Batch and PostgreSQL boundaries of the ticket?
  **PASS** — `CloseRunService` chunk(1), `GroupWriter.write`; before/after commit ITs; private exporter only, live package/handoff outside INT-04 left untouched.
- **S06** · Are dependencies constructor-injected and mutable state scoped to the run or group?
  **PASS** — production constructors, per-run `AccountGroups`/`GroupCalculation`, local group total and per-run ordinal; `InterestCloseApplication` bean wiring.
- **S07** · Are scale, receiver width and rounding exact, with no floating-point types?
  **PASS** — `InterestRules.monthly`/`receiver`; `INT_R01_INT_Q01_tiesAndI07NonTiesTruncateBeforeSumming`, negative/width/overflow tests.
- **S08** · Are encoding, sign and framing confined to the boundary and verified profile?
  **PASS** — `SnapshotIntake.decodeLines`, `CloseStore`, `CloseExporter` use frozen codec LAB_ASCII; golden-byte ITs and unchanged codec tests; host profile explicitly unverified.
- **S09** · Are the opaque PARM and local timestamp source injected independently?
  **PASS** — `InterestRules(Clock)`, per-run `Identity.parm`; `INT_H02_opaqueTenPrintableBytesOnly` and `INT_Q05_TN_02_advancingClockSamplesOncePerEmittedRow`.
- **S10** · Does each touched quirk/OPEN decision have one named implementation point and an ID in code and test name?
  **PASS** — INT-H06 at `CloseRunService.run` / `INT_H06_A3_resumeChangedIdentityRefusedAndNoAutomaticRestart`; INT-H04 at `GroupCalculation.process` / `INT_Q04_INT_H04_nonzeroRateZeroAmountStillEmitsForInactiveExpiredAccount`; TN-03/04/07/09 at `LegacyStatus` / `LegacyStatusTest`.
- **S11** · Are discrepancies routed to owners, decisions left OPEN and unproven host outcomes held?
  **PASS** — owner/evidence register gaps in `parity-report.md`; multi-card intake hold IT; only shadow/private compute, no live release or edited decisions.
- **S12** · Does each touched status path produce the specified outcome, exit and diagnostic?
  **PASS** — `LegacyStatus.check` distinguishes success, TCATBALF-only EOF, exact-rate fallback and typed terminal failure; `LegacyStatusTest` verifies all 17 source operation/text combinations. `INT_R10_TN_03_TN_04_TN_07_TN_09_typedOperationFailuresRollbackAndPreserveRestrictedSourceOrder` proves FAILED/U999/RC12, restricted diagnostics, rollback and no dataset export for injected typed failures.
- **S13** · Do private restart and host hold/reconciliation paths preserve committed state and prevent duplicate effects?
  **PASS** — `INT_H06_A3_crashBeforeGroupCommitRollsBackWholeGroupAndExplicitResumeSeeksCheckpoint`, `INT_H06_A3_crashAfterCommitDoesNotReplayCommittedOrdinalOrClock`, `INT_H06_A3_operatorAbandonedComputingRequiresExplicitResumeAndPreservesCheckpoint`, changed identity/snapshot ITs; explicit operator abandonment only, no automatic restart or host reconciliation/release added.
- **S14** · Are ordinary logs masked and parity SYSOUT restricted?
  **PASS** — `GroupCalculation.rate`/`MissingLookup`, `CloseRunService` category/run logging, `CloseExporter.transcript`; `close.properties` suppresses framework logging in candidate launch. Raw synthetic records only in restricted parity artifacts.
- **S15** · Do applicable rule-named tests cover their listed fixtures and targeted gaps with independent expectations?
  **PASS** — fresh `LegacyStatusTest` has six independent numeric/extended/profile, exact operation-text, EOF/fallback and malformed-status probes; new typed-operation failure IT plus existing required-lookup/restart/intake tests and seven fixture comparisons. See `restart-and-failure-cases.txt` for the actual executed cases.
- **S16** · Is required PostgreSQL 15, field/RC/SYSOUT and byte evidence present at the ticket's scope?
  **PASS** — `test-output.txt`, seven-case `compare-report.md`, `i01-sql-output.txt`, golden-byte and distinctive-filler ITs; no H2 or rounded-total substitution.
- **S17** · Are legacy source, fixtures, goldens and comparator untouched?
  **PASS** — `scope.txt`; only allowed new code/test/evidence paths differ; `make-parity.txt` has 24 PASS.
- **S18** · Are frozen interfaces untouched and new dependencies justified in the PR?
  **PASS** — `scope.txt`; no new dependencies, no pom/codec/schema/migration/pilot/shared-type edits. Existing PostgreSQL 15/Testcontainers dependencies reused.
- **S19** · Is all supplied data synthetic?
  **PASS** — committed fixture inputs plus synthetic malformed/AIX/overflow/filler specimens in `InterestCloseIT` and `GroupCalculationTest`; no production/host data supplied.
- **a** · Does the stored cent value truncate toward zero before summing, including positive and negative fractions?
  **PASS** — `INT_R01_INT_Q01_tiesAndI07NonTiesTruncateBeforeSumming`, `INT_Q03_INT_H07_negativeSubCentUsesDownNotFloorAndNegativeRateIsNotFiltered`; i02/i06/i07 comparator evidence.
- **b** · Do receiver widths and suffix wrap retain low digits without dropping emitted rows?
  **PASS** — `INT_H05_receiverWidthsKeepSignedLowDigitsAtEveryAssignmentAndAddition`, `INT_H05_INT_R08_overflowKeepsEveryRowAndWrapsSuffixAndGroupTotal`.
- **c** · Is intermediate product precision preserved and host arithmetic evidence still called out?
  **PASS** — exact `BigDecimal.multiply` in `InterestRules.monthly`; tie/overflow tests; ARITH/compiler evidence remains a mainframe question in `parity-report.md`.
- **d** · Are signed zoned values handled at the boundary while unsigned fields preserve digit width?
  **PASS** — unchanged frozen codec contract/tests in `test-output.txt`; i06 golden bytes, `InterestRules.transactionId` suffix tests; LAB_ASCII only.
- **e** · Is COMP/BINARY isolated to internal outcomes and diagnostics, with host options unresolved pending evidence?
  **PASS** — `LegacyStatus.format` isolates the two-byte diagnostics overlay; `TN_04_INT_R10_profileControlsCharacterAndRightByteOverlayWithoutClaimingHostLayout` independently checks CP037 and synthetic little-endian PIC-999 truncation. No binary business fields or host compiler assumptions added.
- **g** · Do PAD/MOVE width, fixed comparison, STRING fields and status slices match the legacy layout?
  **PASS** — `INT_R05_INT_H01_INT_Q05_TN_06_constantsNoFeesAndOneTimestampSample`, padded DEFAULT lookup, missing-lookup SYSOUT assertions and `TN_04_INT_R10_numericStatusUsesExactFourPositionMove` / extended-status byte probes.
- **h** · Does ordering/seek use encoded unsigned key bytes and distinguish the lab profile from host evidence?
  **PASS** — BYTEA `ORDER BY legacy_key`, `FixedRecordCodec.compareKeys` in `AccountGroups`, resume/key-tamper IT; `parity-report.md` does not claim host CCSID/collation evidence.
- **i** · Are both overlays accounted for by a timestamp formatter and a profile-aware status adapter?
  **PASS** — `InterestRules.timestamp` advancing-clock test plus `LegacyStatus.format` and `TN_04_INT_R10_profileControlsCharacterAndRightByteOverlayWithoutClaimingHostLayout`; encoding and binary overlay interpretation are explicit lab/synthetic profiles, not claimed production-host evidence.
- **j** · Are all LRECLs and filler/untouched bytes preserved in byte tests?
  **PASS** — frozen codec tests, all-seven golden-byte IT, `TN_06_INT_R06_INT_R07_exportPatchesOnlyThreeFieldsAndPreservesDistinctiveFiller` (300-byte account) and 350-byte transaction unit assertion.
- **l** · Does state persist within its intended scope without reliance on unassigned bytes?
  **PASS** — per-run ordinal/group total and `InterestRules.transaction` explicit spaces; multi-category/overflow and constants tests.
  For newly emitted transactions in the lab fixture profile, explicitly fill TRAN-RECORD
  **PASS** — `InterestRules.transaction` constructs the complete immutable transaction record.
  FILLER and the unused TRAN-DESC tail with spaces, as `InterestTransaction` does in INT-01.
  **PASS** — `INT_R05_INT_H01_INT_Q05_TN_06_constantsNoFeesAndOneTimestampSample` independently checks filler and description tail.
  This documents the pilot's choice, not the legacy host bytes; host initialization remains
  **PASS** — `parity-report.md` explicitly limits this choice to lab evidence.
  unconfirmed (§4 item l).
  **PASS** — host initialization remains in the “Confirm on the mainframe” owner/evidence gaps; no decision register edits.
- **m** · Are normal breaks, EOF and empty input covered, including the withheld final delta?
  **PASS** — `INT_R09_INT_Q02_lookaheadNextKeyAndResumeKeepFinalGroupWithoutFinalDelta`, `INT_Q02_INT_Q06_INT_H09_emptyAndSingleAccountNeverProduceDeltaOrClearInputs`; terminal checkpoint SQL assertion.
- **o** · Are success and EOF typed outcomes with no APPL-RESULT leakage into RC?
  **PASS** — Spring Batch `ItemReader` null EOF contract, `Group` lookahead, `CloseRunService.Result`; empty-input IT returns RC0 and no delta.
- **p** · Do keyed reads, nonunique AIX selection and guarded rewrite use the recorded key and preimage contracts?
  **PASS** — `CloseStore.account`/`card`/`rate`/`delta` records original balance/cycles/raw SHA256; AIX-evidence IT; exporter codec-patch/filler IT. Actual host guarded rewrite is outside INT-04 and unchanged.
- **q** · Do EOF, fallback and terminal status/abend paths match the file and operation-specific rules?
  **PASS** — `LegacyStatusTest.TN_03_TN_09_INT_R10_everySupportedOperationUsesItsCobolDisplayText` asserts all source OPEN/CLOSE/READ/WRITE/REWRITE diagnostics, including DISCGRP open and XREF open's adjacent status. The typed-operation IT checks exact source-ordered terminal SYSOUT/U999; non-TCATBALF 10 fails, exact-rate 23 alone falls back.
- **r** · Do PARM, fixed GDG output, DISP intent and step RC drive the correct handoff and hold paths?
  **PASS** — invalid-PARM IT, `CloseExporter` private fixed-generation artifacts and no failed-run datasets, seven real-driver RC comparisons; live DISP/catalog/release explicitly outside INT-04 and not approved.
- **s** · Does restricted SYSOUT match ordered DISPLAY including skipped inputs and fallback lines?
  **PASS** — `CloseExporter.transcript`, all-seven golden SYSOUT bytes and comparator evidence including i03 skip and i04 fallback.
- **t** · Do IDs and timestamps use their distinct verified inputs?
  **PASS** — independently injected PARM and Clock in `InterestRules`; i01 prefix 2022071800 differs from timestamp date 2022-07-05; constants/suffix tests.
- **u** · Do both timestamp fields share one sample, truncate to hundredths and append four zeros?
  **PASS** — `INT_Q05_TN_02_advancingClockSamplesOncePerEmittedRow` and transaction constants test verify .129999 -> .120000 and equal timestamps.
- **w** · Are fee, filter, blanket reset and category-clear paths absent from the target calculation?
  **PASS** — `INT_Q04_INT_H04_nonzeroRateZeroAmountStillEmitsForInactiveExpiredAccount`, unchanged input/digest assertions, empty/single-account IT; `GroupCalculation` only interest, no fee/filter/category mutation path.
