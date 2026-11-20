# INT-05 standards §9 checklist

Revision: the INT-05 commit containing this file (`git log -1 --format=%H -- java/interest/evidence/05-shadow-handoff/checklist.md`); combined check run on b933d3c (revision.txt).

- **S01** · Does the PR trace the assigned ticket and rules through the design/map to the source ranges used?
  **PASS** — Ticket 05-shadow-handoff → INT-ARCH A1 (publication ledger), A2.4/A3 Publish row, A4 bridge; INT-MAP CVTRA05Y T01-T14 (350) and CVACT01Y A03/A09/A10; JCL INTCALC STEP15 TRANSACT DD SYSTRAN(+1) RECFM=F LRECL=350; parity-report.md §Trace.
- **S02** · Do assertions use recorded legacy outputs, and are conflicting claims cited for the register owner?
  **PASS** — Packet bytes asserted against recorded golden SYSTRAN.G0001V00 and ACCTDATA post-image (HandoffIT.INT_R08_packetIsDeterministic..., verifiedMatchingReceipt...); digest independently recomputed from the golden (parity-report.md). No conflicting claim found.
- **S03** · Are operations and typed outcomes rule-driven, without paragraph translation, procedural flags or COBOL control-flow emulation?
  **PASS** — Sealed HandoffOutcome (ShadowRehearsed/Released/Held) and HoldReason enum; HandoffCoordinator is guard-ordered, no paragraph/flag emulation.
- **S04** · Are business records immutable, with COBOL names at their bindings and original bytes protected?
  **PASS** — ExportPacket/PayloadRecord/GuardedDelta immutable records with defensive copies; original raw_record bytes carried unchanged (HandoffRulesTest.packetIsImmutable).
- **S05** · Do compute, package and handoff respect the Spring Batch and PostgreSQL boundaries of the ticket?
  **PASS** — Packaging reads only immutable staged rows (PacketBuilder); publication ledger writes run under SET ROLE interest_publish (HandoffIT ROLE line) to interest.publication + run.state only; handoff is outside compute and not an item writer; shadow writes PRIVATE, no host call (shadowMode... host_calls=0).
- **S06** · Are dependencies constructor-injected and mutable state scoped to the run or group?
  **PASS** — PacketBuilder, PublicationLedger, HandoffCoordinator constructor-injected; no static mutable state.
- **S07** · Are scale, receiver width and rounding exact, with no floating-point types?
  **PASS** — BigDecimal only; post-images encoded through FixedRecordCodec.encode(Decoded, patch) with receiver width checks; no float/double in handoff.
- **S08** · Are encoding, sign and framing confined to the boundary and verified profile?
  **PASS** — Fixed records stay codec-bound; payload is count×350 with no LF/RDW (ExportPacket.payload); LF only in the test fixture adapter.
- **S09** · Are the opaque PARM and local timestamp source injected independently?
  **PASS** — Not touched: PARM and timestamps are taken verbatim from staged rows and recorded in the manifest; no clock sampling in handoff.
- **S10** · Does each touched quirk/OPEN decision have one named implementation point and an ID in code and test name?
  **PASS** — INT-H06 → HandoffCoordinator / HoldReason (tests INT_H06_*); INT-H05 → TXN_ID_COLLISION (INT_H05_backupTransactionIdCollision...); INT-R06/R07 → GuardedDelta (INT_R06_wrongAccountPreimage...); INT-R08 → emit-ordinal payload order (INT_R08_packetIsDeterministic...).
- **S11** · Are discrepancies routed to owners, decisions left OPEN and unproven host outcomes held?
  **PASS** — INT-H05/H06 remain OPEN; HostBridge has no production implementation; unknown apply/catalog/receipt → HELD (INT_H06_unknownApplyOutcome, INT_H06_ambiguousCatalog..., INT_H06_failedCatalog..., INT_H06_missingReceipt...).
- **S12** · Does each touched status path produce the specified outcome, exit and diagnostic?
  **PASS** — Typed Held reasons per path (handoff-fault-injection.txt FAULT lines); packet rejections typed PacketRejected (tamperedStagedRecord, ordinalGap, stalePreimageFingerprint, runNotComputed); success condition only on Released.
- **S13** · Do private restart and host hold/reconciliation paths preserve committed state and prevent duplicate effects?
  **PASS** — One row per run + one live claim per destination/cycle under advisory lock; held runs resolve only via verified receipt, never reapply (INT_H06_repeatedVerifiedReceipt..., INT_H06_partialAccountApply..., INT_H06_secondLiveRunForSame...); SQL invariants after every test.
- **S14** · Are ordinary logs masked and parity SYSOUT restricted?
  **PASS** — Handoff logs only run id, counts, reason and generation; no account values, card numbers or raw bytes (HandoffCoordinator log.info calls).
- **S15** · Do applicable rule-named tests cover their listed fixtures and targeted gaps with independent expectations?
  **PASS** — 29 rule-named handoff tests (HandoffIT 22, HandoffRulesTest 7) on i01-shaped rows with golden-derived expectations; each fault isolated with a fresh run.
- **S16** · Is required PostgreSQL 15, field/RC/SYSOUT and byte evidence present at the ticket's scope?
  **PASS** — PostgreSQL 15.20 Testcontainers (test-output.txt DB line); i01-sql-output.txt; compare-report.md 7/7; make-parity.txt 24/24; payload SHA-256 4808df2a… 700 bytes.
- **S17** · Are legacy source, fixtures, goldens and comparator untouched?
  **PASS** — git diff origin/demo/05b-integration --stat: no app/, fixtures/, tools/parity/, modernization/ changes.
- **S18** · Are frozen interfaces untouched and new dependencies justified in the PR?
  **PASS** — V001/V002 schema, codec and pilot interfaces untouched; no new dependencies (pom.xml unchanged).
- **S19** · Is all supplied data synthetic?
  **PASS** — Only synthetic i01 fixture rows and synthetic byte patterns.
- **a** · Does the stored cent value truncate toward zero before summing, including positive and negative fractions?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **b** · Do receiver widths and suffix wrap retain low digits without dropping emitted rows?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **c** · Is intermediate product precision preserved and host arithmetic evidence still called out?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **d** · Are signed zoned values handled at the boundary while unsigned fields preserve digit width?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **e** · Is COMP/BINARY isolated to internal outcomes and diagnostics, with host options unresolved pending evidence?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **g** · Do PAD/MOVE width, fixed comparison, STRING fields and status slices match the legacy layout?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **h** · Does ordering/seek use encoded unsigned key bytes and distinguish the lab profile from host evidence?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **i** · Are both overlays accounted for by a timestamp formatter and a profile-aware status adapter?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **j** · Are all LRECLs and filler/untouched bytes preserved in byte tests?
  **PASS** — 350-byte geometry enforced (PayloadRecord, PacketBuilder RECORD_GEOMETRY, payloadRecordRejectsWrongGeometry); account post-image keeps all untouched bytes/filler (equals golden ACCTDATA record).
- **l** · Does state persist within its intended scope without reliance on unassigned bytes?
  For newly emitted transactions in the lab fixture profile, explicitly fill TRAN-RECORD
  FILLER and the unused TRAN-DESC tail with spaces, as `InterestTransaction` does in INT-01.
  This documents the pilot's choice, not the legacy host bytes; host initialization remains
  unconfirmed (§4 item l).
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **m** · Are normal breaks, EOF and empty input covered, including the withheld final delta?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **o** · Are success and EOF typed outcomes with no APPL-RESULT leakage into RC?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **p** · Do keyed reads, nonunique AIX selection and guarded rewrite use the recorded key and preimage contracts?
  **PASS** — Guarded rewrite: preimage SHA-256 checked when packaging (DELTA_PREIMAGE), at host preflight (PREIMAGE_MISMATCH) and conditional apply; receipt checks post-image SHA-256 (receiptMustMatchEveryPacketFact).
- **q** · Do EOF, fallback and terminal status/abend paths match the file and operation-specific rules?
  **PASS** — Terminal/hold paths: every failure Held, no fallback execution; packet faults rejected before any claim (HandoffIT reject tests).
- **r** · Do PARM, fixed GDG output, DISP intent and step RC drive the correct handoff and hold paths?
  **PASS** — Live handoff catalogs exactly one SYSTRAN(+1) RECFM=F LRECL=350 relative to observed (0) (nextGeneration tests, GENERATION_MOVED hold); success condition (step RC 0 analogue) only after verified receipt; shadow never.
- **s** · Does restricted SYSOUT match ordered DISPLAY including skipped inputs and fallback lines?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **t** · Do IDs and timestamps use their distinct verified inputs?
  **PASS** — Transaction IDs carried from staged rows and checked against T01 (RECORD_COLUMN_MISMATCH); collision preflight INT_H05.
- **u** · Do both timestamp fields share one sample, truncate to hundredths and append four zeros?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.
- **w** · Are fee, filter, blanket reset and category-clear paths absent from the target calculation?
  **PASS** — Out of INT-05 scope; diff is limited to the handoff package and evidence (git diff --stat), behaviour unchanged; compare-report.md 7/7 PASS.

Totals: 38 PASS, 0 FAIL.
