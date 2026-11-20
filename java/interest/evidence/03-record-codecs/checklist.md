# INT-03 standards §9 checklist

- **S01** · Does the PR trace the assigned ticket and rules through the design/map to the source ranges used?
  **PASS** — INT-03 report trace to INT-MAP and copybooks.
- **S02** · Do assertions use recorded legacy outputs, and are conflicting claims cited for the register owner?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S03** · Are operations and typed outcomes rule-driven, without paragraph translation, procedural flags or COBOL control-flow emulation?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S04** · Are business records immutable, with COBOL names at their bindings and original bytes protected?
  **PASS** — FixedRecordCodec business records and defensive Bytes; signed-zero immutability test.
- **S05** · Do compute, package and handoff respect the Spring Batch and PostgreSQL boundaries of the ticket?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S06** · Are dependencies constructor-injected and mutable state scoped to the run or group?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S07** · Are scale, receiver width and rounding exact, with no floating-point types?
  **PASS** — BigDecimal scale UNNECESSARY, explicit receiver width; codec rejection tests.
- **S08** · Are encoding, sign and framing confined to the boundary and verified profile?
  **PASS** — Five layouts and two named profiles; fixture and synthetic tests.
- **S09** · Are the opaque PARM and local timestamp source injected independently?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S10** · Does each touched quirk/OPEN decision have one named implementation point and an ID in code and test name?
  **PASS** — INT_Q03, INT_R08, INT_Q05, INT_R09 and INT_H03 test names.
- **S11** · Are discrepancies routed to owners, decisions left OPEN and unproven host outcomes held?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S12** · Does each touched status path produce the specified outcome, exit and diagnostic?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S13** · Do private restart and host hold/reconciliation paths preserve committed state and prevent duplicate effects?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S14** · Are ordinary logs masked and parity SYSOUT restricted?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **S15** · Do applicable rule-named tests cover their listed fixtures and targeted gaps with independent expectations?
  **PASS** — codec-cases.txt; independent fixture and synthetic expected values.
- **S16** · Is required PostgreSQL 15, field/RC/SYSOUT and byte evidence present at the ticket's scope?
  **PASS** — test-output.txt; compare-report.md; codec-cases.txt.
- **S17** · Are legacy source, fixtures, goldens and comparator untouched?
  **PASS** — Scoped diff leaves app, fixtures and tools/parity unchanged.
- **S18** · Are frozen interfaces untouched and new dependencies justified in the PR?
  **PASS** — Only codec and evidence additions; no dependencies.
- **S19** · Is all supplied data synthetic?
  **PASS** — Committed synthetic fixtures and synthetic test values only.
- **a** · Does the stored cent value truncate toward zero before summing, including positive and negative fractions?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **b** · Do receiver widths and suffix wrap retain low digits without dropping emitted rows?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **c** · Is intermediate product precision preserved and host arithmetic evidence still called out?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **d** · Are signed zoned values handled at the boundary while unsigned fields preserve digit width?
  **PASS** — Negative T06, unsigned leading-zero and signed-zero tests.
- **e** · Is COMP/BINARY isolated to internal outcomes and diagnostics, with host options unresolved pending evidence?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **g** · Do PAD/MOVE width, fixed comparison, STRING fields and status slices match the legacy layout?
  **PASS** — Exact width/padding tests and fixed transaction text assertions.
- **h** · Does ordering/seek use encoded unsigned key bytes and distinguish the lab profile from host evidence?
  **PASS** — INT_R09 mixed A1/01 encoded-key order tests.
- **i** · Are both overlays accounted for by a timestamp formatter and a profile-aware status adapter?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **j** · Are all LRECLs and filler/untouched bytes preserved in byte tests?
  **PASS** — TN_06 raw/filler and account unchanged-byte tests.
- **l** · Does state persist within its intended scope without reliance on unassigned bytes?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
  For newly emitted transactions in the lab fixture profile, explicitly fill TRAN-RECORD
  FILLER and the unused TRAN-DESC tail with spaces, as `InterestTransaction` does in INT-01.
  This documents the pilot's choice, not the legacy host bytes; host initialization remains
  unconfirmed (§4 item l).
- **m** · Are normal breaks, EOF and empty input covered, including the withheld final delta?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **o** · Are success and EOF typed outcomes with no APPL-RESULT leakage into RC?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **p** · Do keyed reads, nonunique AIX selection and guarded rewrite use the recorded key and preimage contracts?
  **PASS** — XREF exact base/AIX bytes; no first-card selection added.
- **q** · Do EOF, fallback and terminal status/abend paths match the file and operation-specific rules?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **r** · Do PARM, fixed GDG output, DISP intent and step RC drive the correct handoff and hold paths?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **s** · Does restricted SYSOUT match ordered DISPLAY including skipped inputs and fallback lines?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **t** · Do IDs and timestamps use their distinct verified inputs?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **u** · Do both timestamp fields share one sample, truncate to hundredths and append four zeros?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
- **w** · Are fee, filter, blanket reset and category-clear paths absent from the target calculation?
  **PASS** — Out-of-scope behavior left untouched by codec/evidence-only diff; compare-report.md regression.
