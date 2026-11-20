# INT-02 standards §9 checklist

Scope: only SQL, schema IT and evidence. No calculation/codec/host behavior changed.

- **S01** · Does the PR trace the assigned ticket and rules through the design/map to the source ranges used?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **S02** · Do assertions use recorded legacy outputs, and are conflicting claims cited for the register owner?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S03** · Are operations and typed outcomes rule-driven, without paragraph translation, procedural flags or COBOL control-flow emulation?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S04** · Are business records immutable, with COBOL names at their bindings and original bytes protected?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S05** · Do compute, package and handoff respect the Spring Batch and PostgreSQL boundaries of the ticket?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S06** · Are dependencies constructor-injected and mutable state scoped to the run or group?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S07** · Are scale, receiver width and rounding exact, with no floating-point types?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **S08** · Are encoding, sign and framing confined to the boundary and verified profile?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S09** · Are the opaque PARM and local timestamp source injected independently?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S10** · Does each touched quirk/OPEN decision have one named implementation point and an ID in code and test name?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **S11** · Are discrepancies routed to owners, decisions left OPEN and unproven host outcomes held?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S12** · Does each touched status path produce the specified outcome, exit and diagnostic?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S13** · Do private restart and host hold/reconciliation paths preserve committed state and prevent duplicate effects?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S14** · Are ordinary logs masked and parity SYSOUT restricted?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S15** · Do applicable rule-named tests cover their listed fixtures and targeted gaps with independent expectations?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **S16** · Is required PostgreSQL 15, field/RC/SYSOUT and byte evidence present at the ticket's scope?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **S17** · Are legacy source, fixtures, goldens and comparator untouched?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **S18** · Are frozen interfaces untouched and new dependencies justified in the PR?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **S19** · Is all supplied data synthetic?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **a** · Does the stored cent value truncate toward zero before summing, including positive and negative fractions?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **b** · Do receiver widths and suffix wrap retain low digits without dropping emitted rows?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **c** · Is intermediate product precision preserved and host arithmetic evidence still called out?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **d** · Are signed zoned values handled at the boundary while unsigned fields preserve digit width?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **e** · Is COMP/BINARY isolated to internal outcomes and diagnostics, with host options unresolved pending evidence?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **g** · Do PAD/MOVE width, fixed comparison, STRING fields and status slices match the legacy layout?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **h** · Does ordering/seek use encoded unsigned key bytes and distinguish the lab profile from host evidence?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **i** · Are both overlays accounted for by a timestamp formatter and a profile-aware status adapter?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **j** · Are all LRECLs and filler/untouched bytes preserved in byte tests?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **l** · Does state persist within its intended scope without reliance on unassigned bytes?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
  For newly emitted transactions in the lab fixture profile, explicitly fill TRAN-RECORD
  FILLER and the unused TRAN-DESC tail with spaces, as `InterestTransaction` does in INT-01.
  This documents the pilot's choice, not the legacy host bytes; host initialization remains
  unconfirmed (§4 item l).
- **m** · Are normal breaks, EOF and empty input covered, including the withheld final delta?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **o** · Are success and EOF typed outcomes with no APPL-RESULT leakage into RC?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **p** · Do keyed reads, nonunique AIX selection and guarded rewrite use the recorded key and preimage contracts?
  **PASS** — V001/V002 SQL; SchemaIT; schema-assertions.txt; compare-report.md.
- **q** · Do EOF, fallback and terminal status/abend paths match the file and operation-specific rules?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **r** · Do PARM, fixed GDG output, DISP intent and step RC drive the correct handoff and hold paths?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **s** · Does restricted SYSOUT match ordered DISPLAY including skipped inputs and fallback lines?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **t** · Do IDs and timestamps use their distinct verified inputs?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **u** · Do both timestamp fields share one sample, truncate to hundredths and append four zeros?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
- **w** · Are fee, filter, blanket reset and category-clear paths absent from the target calculation?
  **PASS** — Scoped diff leaves runtime contracts untouched; compare-report.md and make-parity.txt regression evidence.
