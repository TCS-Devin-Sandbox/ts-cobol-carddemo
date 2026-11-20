# INT-06 conversion standards §9 checklist

Code: `77e37b12c3c032d929ab6452f67ae040832e935f`.

The questions below are copied verbatim from interest-conversion-standards §9,
including its multi-line l item. PASS is scoped to this diff and the evidenced
lab contract, not production approval. Unchanged/out-of-scope implementations
are explicitly identified. File references below are relative to
`java/interest/interest-batch/`; Java area references are beneath
`src/main/java/com/carddemo/interest/` or `src/test/java/com/carddemo/interest/`
as indicated by symbols. Raw evidence names are relative to this directory.
All OPEN owner decisions and host evidence prerequisites remain OPEN.

- **S01** · Does the PR trace the assigned ticket and rules through the design/map to the source ranges used?

  **PASS** — `docs/verification/INT-06-release-readiness.md` maps INT-ARCH A3/A4 and decision/manifest references; `gate/CandidateParityGateIT.java#INT_R01_R10_Q01_Q02_Q03_candidateMatchesGoldenBytesAndDatabase` verifies the joined contract. Existing rule/codec implementations and cited legacy sources are untouched (`scope.txt`).

- **S02** · Do assertions use recorded legacy outputs, and are conflicting claims cited for the register owner?

  **PASS** — `gate/GateSupport.java#facts` and `scripts/parity/byte_check.py#golden_facts` use committed goldens/input independently. `parity-report.md` flags the final-account README conflict for the existing Finance/card operations decision owner; no oracle changed.

- **S03** · Are operations and typed outcomes rule-driven, without paragraph translation, procedural flags or COBOL control-flow emulation?

  **PASS** — `batch/InterestJobConfig.java#interestJob` delegates computation and accepts typed `HandoffOutcome.ShadowRehearsed`; `gate/ReleaseRehearsalIT.java` asserts typed Held/Released outcomes, not translated COBOL flow.

- **S04** · Are business records immutable, with COBOL names at their bindings and original bytes protected?

  **PASS** — Business records/codecs remain untouched (`scope.txt`); `gate/GateHost.java` clones retained images; `gate/GateSupport.java#records` checks originals without patching them.

- **S05** · Do compute, package and handoff respect the Spring Batch and PostgreSQL boundaries of the ticket?

  **PASS** — `batch/InterestJobConfig.java#interestJob` keeps both orchestration tasklets outside a wrapping transaction; existing group transactions remain in CloseRunService. `batch/InterestBatchIT.java#INT_H06_i01_computesThenPackagesPrivately` proves compute then package order.

- **S06** · Are dependencies constructor-injected and mutable state scoped to the run or group?

  **PASS** — `batch/InterestJobConfig.java` and `batch/InterestBatchApplication.java` receive dependencies through bean method injection; per-run result lives in JobExecution context, not singleton mutable run state. Existing domain constructors unchanged (`scope.txt`).

- **S07** · Are scale, receiver width and rounding exact, with no floating-point types?

  **PASS** — Arithmetic implementation untouched (`scope.txt`); `gate/CandidateParityGateIT.java#INT_R01_R10_Q01_Q02_Q03_candidateMatchesGoldenBytesAndDatabase` compares exact BigDecimal signed sums. Python golden facts use Decimal, not float.

- **S08** · Are encoding, sign and framing confined to the boundary and verified profile?

  **PASS** — `scripts/parity/byte_check.py#records` restores fixture LRECL at the boundary; `gate/GateSupport.java#rows` uses LAB_ASCII codec framing; `byte-check.txt` covers all 14 dataset files.

- **S09** · Are the opaque PARM and local timestamp source injected independently?

  **PASS** — `batch/InterestBatchApplication.java#closeClock` and `batch/InterestJobConfig.java#interestJob` reuse separate clock/zone and PARM properties. Existing `pilot/LegacyTimestampTest.java#INT_Q05_clockIsIndependentOfParm` remains unchanged and passes in `test-output.txt`.

- **S10** · Does each touched quirk/OPEN decision have one named implementation point and an ID in code and test name?

  **PASS** — No new rule implementations; existing quirk points unchanged (`scope.txt`). `gate/CandidateParityGateIT.java#INT_R01_R10_Q01_Q02_Q03_candidateMatchesGoldenBytesAndDatabase` covers Q01/Q02/Q03; all recovery rehearsals in `gate/ReleaseRehearsalIT.java` are named INT_H06.

- **S11** · Are discrepancies routed to owners, decisions left OPEN and unproven host outcomes held?

  **PASS** — Register/manifest owner references and all OPEN rows remain open in `docs/verification/INT-06-release-readiness.md`; `parity-report.md` records README discrepancy, not a newly resolved policy. `gate/ReleaseRehearsalIT.java#INT_H06_stalePreimageHoldsBeforeMutation` and other rehearsals hold uncertain effects; no external owner approval claimed.

- **S12** · Does each touched status path produce the specified outcome, exit and diagnostic?

  **PASS** — `gate/CandidateParityGateIT.java#INT_R10_failuresHaveNoPublishedEffects` covers five failure specimens; `batch/InterestBatchIT.java#INT_H06_packageRejectionRemovesSuccessArtifacts` covers packaging rejection. Existing typed status adapter tests in `calc/LegacyStatusTest.java` remain passing (`test-output.txt`).

- **S13** · Do private restart and host hold/reconciliation paths preserve committed state and prevent duplicate effects?

  **PASS** — `gate/ReleaseRehearsalIT.java#INT_H06_unknownApplyAndMissingReceiptNeverReapply`, `#INT_H06_partialAccountApplyRestoresOnlyJournaledSubset` and `#INT_H06_postConsumptionContainsWithoutRepublishOrAutomaticRerun`; existing private restart tests in `job/InterestCloseIT.java` unchanged and passing (`test-output.txt`).

- **S14** · Are ordinary logs masked and parity SYSOUT restricted?

  **PASS** — `batch/InterestJobConfig.java` logs only run/count/digest/category; `e2e-run.log` shows package-shadow events. Restricted transcript remains in sysout files and is checked by `gate/CandidateParityGateIT.java`; no new identifiers/balances in production log statements.

- **S15** · Do applicable rule-named tests cover their listed fixtures and targeted gaps with independent expectations?

  **PASS** — `gate/CandidateParityGateIT.java` supplies independent golden bytes/counts/sums for seven cases and five error specimens; `gate/ReleaseRehearsalIT.java` covers all A4 rehearsal asks; `test-output.txt` records 150 tests, no skips.

- **S16** · Is required PostgreSQL 15, field/RC/SYSOUT and byte evidence present at the ticket's scope?

  **PASS** — `test-output.txt`, `compare-stdout.txt`, `compare-report.md`, `byte-check.txt`, `sql-output.txt` and `gate-output.txt`; real PG15, seven field/RC/SYSOUT passes and 14 padded-byte passes.

- **S17** · Are legacy source, fixtures, goldens and comparator untouched?

  **PASS** — `scope.txt`: only granted batch orchestration/tests plus gate/scripts/docs/evidence; no app, fixtures, tools/parity or design files changed.

- **S18** · Are frozen interfaces untouched and new dependencies justified in the PR?

  **PASS** — `scope.txt` shows no pom, migration, shared interface or dependency change. No new dependency is introduced; PR publication is outside this evidence step.

- **S19** · Is all supplied data synthetic?

  **PASS** — All inputs are committed synthetic CardDemo fixtures/specimens; the additional combined-input rehearsal merges only i01/i02 (`gate/ReleaseRehearsalIT.java#INT_H06_partialAccountApplyRestoresOnlyJournaledSubset`).

- **a** · Does the stored cent value truncate toward zero before summing, including positive and negative fractions?

  **PASS** — Arithmetic implementation untouched (`scope.txt`). Existing `calc/InterestRulesTest.java#INT_R01_INT_Q01_tiesAndI07NonTiesTruncateBeforeSumming` and `#INT_Q03_INT_H07_negativeSubCentUsesDownNotFloorAndNegativeRateIsNotFiltered` pass (`test-output.txt`); i02/i06/i07 golden sums in `sql-output.txt`.

- **b** · Do receiver widths and suffix wrap retain low digits without dropping emitted rows?

  **PASS** — Width/wrap implementation untouched (`scope.txt`). Existing `calc/InterestRulesTest.java#INT_H05_receiverWidthsKeepSignedLowDigitsAtEveryAssignmentAndAddition` and `#INT_R08_INT_H05_suffixWrapDoesNotLimitOrdinal` pass (`test-output.txt`).

- **c** · Is intermediate product precision preserved and host arithmetic evidence still called out?

  **PASS** — Arithmetic implementation untouched (`scope.txt`); `calc/InterestRulesTest.java#INT_R01_INT_Q01_tiesAndI07NonTiesTruncateBeforeSumming` passes. Host compiler/arithmetic certification remains explicitly unverified in `docs/verification/INT-06-release-readiness.md`.

- **d** · Are signed zoned values handled at the boundary while unsigned fields preserve digit width?

  **PASS** — Codec implementation untouched (`scope.txt`). Existing `codec/FixedRecordCodecTest.java#INT_Q03_T06_negativeAmountAndLeadingZeroFieldsIndependent` passes; `scripts/parity/test_byte_check.py#test_signed_zoned_golden_amounts` independently checks positive/negative zoned facts.

- **e** · Is COMP/BINARY isolated to internal outcomes and diagnostics, with host options unresolved pending evidence?

  **PASS** — Binary/status implementation untouched (`scope.txt`); `calc/LegacyStatusTest.java#TN_04_INT_R10_profileControlsCharacterAndRightByteOverlayWithoutClaimingHostLayout` passes. Compiler options remain an open host prerequisite in the verification doc.

- **g** · Do PAD/MOVE width, fixed comparison, STRING fields and status slices match the legacy layout?

  **PASS** — Codec/layout implementation untouched (`scope.txt`). `byte-check.txt` verifies LRECL/padding/filler; `calc/LegacyStatusTest.java#TN_04_INT_R10_numericStatusUsesExactFourPositionMove` passes (`test-output.txt`).

- **h** · Does ordering/seek use encoded unsigned key bytes and distinguish the lab profile from host evidence?

  **PASS** — Ordering/seek code untouched (`scope.txt`). Existing `codec/FixedRecordCodecTest.java#INT_R09_INT_H03_encodedKeysAndUnsignedLocaleOrder` and `job/InterestCloseIT.java#INT_H06_A3_crashAfterCommitDoesNotReplayCommittedOrdinalOrClock` pass; verification doc separates lab/host evidence.

- **i** · Are both overlays accounted for by a timestamp formatter and a profile-aware status adapter?

  **PASS** — Timestamp/status adapters untouched (`scope.txt`). Existing `pilot/LegacyTimestampTest.java#INT_Q05_hundredthsTruncateAndFourZerosAppended` and `calc/LegacyStatusTest.java#TN_04_INT_R10_profileControlsCharacterAndRightByteOverlayWithoutClaimingHostLayout` pass (`test-output.txt`).

- **j** · Are all LRECLs and filler/untouched bytes preserved in byte tests?

  **PASS** — `gate/CandidateParityGateIT.java#INT_R01_R10_Q01_Q02_Q03_candidateMatchesGoldenBytesAndDatabase` and `byte-check.txt` cover exact ordered padded bytes for both datasets in all seven fixtures, including fillers.

- **l** · Does state persist within its intended scope without reliance on unassigned bytes?
  For newly emitted transactions in the lab fixture profile, explicitly fill TRAN-RECORD
  FILLER and the unused TRAN-DESC tail with spaces, as `InterestTransaction` does in INT-01.
  This documents the pilot's choice, not the legacy host bytes; host initialization remains
  unconfirmed (§4 item l).

  **PASS** — `batch/InterestJobConfig.java#interestJob` scopes run results to execution context; codec/calculation filler initialization is unchanged (`scope.txt`). All golden filler/unused-tail bytes match (`byte-check.txt`); host initialization explicitly remains unconfirmed in the verification doc.

- **m** · Are normal breaks, EOF and empty input covered, including the withheld final delta?

  **PASS** — `gate/CandidateParityGateIT.java` covers multi-account breaks/final delta exclusion; `batch/InterestBatchIT.java#INT_R09_emptyInputPackagesZeroRecords` and existing `job/InterestCloseIT.java#INT_Q02_INT_Q06_INT_H09_emptyAndSingleAccountNeverProduceDeltaOrClearInputs` pass (`test-output.txt`).

- **o** · Are success and EOF typed outcomes with no APPL-RESULT leakage into RC?

  **PASS** — EOF/status implementation untouched (`scope.txt`). `batch/InterestBatchIT.java#INT_R09_emptyInputPackagesZeroRecords` returns 0; `gate/CandidateParityGateIT.java#INT_R10_failuresHaveNoPublishedEffects` returns 12. `gate-output.txt` records actual process RCs.

- **p** · Do keyed reads, nonunique AIX selection and guarded rewrite use the recorded key and preimage contracts?

  **PASS** — Lookup/AIX/guarded rewrite implementation untouched (`scope.txt`). Existing `job/InterestCloseIT.java#INT_H03_multiCardWithoutEvidenceHeldAndSuppliedFirstReturnNotMinimumIsSealed` passes. `gate/ReleaseRehearsalIT.java#INT_H06_stalePreimageHoldsBeforeMutation` checks exact original fingerprint.

- **q** · Do EOF, fallback and terminal status/abend paths match the file and operation-specific rules?

  **PASS** — Status/formatter implementation untouched (`scope.txt`). Existing `job/InterestCloseIT.java#INT_R10_TN_03_TN_04_TN_07_TN_09_typedOperationFailuresRollbackAndPreserveRestrictedSourceOrder` passes; `compare-report.md` verifies normal/fallback SYSOUT unchanged.

- **r** · Do PARM, fixed GDG output, DISP intent and step RC drive the correct handoff and hold paths?

  **PASS** — `batch/InterestJobConfig.java#interestJob` reuses opaque PARM and gates packaging on RC 0; `gate-output.txt` includes real-setup RC provenance; `gate/ReleaseRehearsalIT.java` verifies one generation/hold paths. Real DISP/fence evidence remains unverified per doc.

- **s** · Does restricted SYSOUT match ordered DISPLAY including skipped inputs and fallback lines?

  **PASS** — `gate/CandidateParityGateIT.java#INT_R01_R10_Q01_Q02_Q03_candidateMatchesGoldenBytesAndDatabase` compares complete ordered SYSOUT against golden for all cases, including i03 skipped rate and i04 fallback; `compare-report.md` independently passes.

- **t** · Do IDs and timestamps use their distinct verified inputs?

  **PASS** — `batch/InterestBatchApplication.java#closeClock` and `batch/InterestJobConfig.java#interestJob` reuse distinct clock/PARM inputs; existing `pilot/LegacyTimestampTest.java#INT_Q05_clockIsIndependentOfParm` passes; ID/timestamp bytes match all goldens (`byte-check.txt`).

- **u** · Do both timestamp fields share one sample, truncate to hundredths and append four zeros?

  **PASS** — Timestamp implementation untouched (`scope.txt`). Existing `calc/InterestRulesTest.java#INT_R05_INT_H01_INT_Q05_TN_06_constantsNoFeesAndOneTimestampSample` and `pilot/LegacyTimestampTest.java#INT_Q05_hundredthsTruncateAndFourZerosAppended` pass (`test-output.txt`).

- **w** · Are fee, filter, blanket reset and category-clear paths absent from the target calculation?

  **PASS** — Calculation/intake implementation untouched (`scope.txt`); existing `job/InterestCloseIT.java#INT_Q04_INT_H04_nonzeroRateZeroAmountStillEmitsForInactiveExpiredAccount` and `#INT_Q02_INT_Q06_INT_H09_emptyAndSingleAccountNeverProduceDeltaOrClearInputs` pass; all seven candidate goldens match.

## Totals

38 PASS; 0 FAIL. No omissions or additional invented letter items.
