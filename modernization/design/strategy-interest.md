# Interest migration strategy — INTCALC

**PROPOSED · Design review only.** Java 21, Spring Batch, PostgreSQL 15. Design baseline `demo/03-rules @ 0dd0a8e672cbdf46479b45b02a5ba8c650de6561`. Estate evidence baseline `demo/01-intake @ 41519206b887cd0123ea850b46a96cdc9baa69af`.

Scope evidence: **5** copybooks, **7** golden cases, **15** OPEN entries in the decision register (six quirks, nine holes). Confirm the items below with the named roles: an approved design is not a production release.

## Move map

`CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL` is the observed scheduler sequence; only the calculation moves. Host datasets → sealed PostgreSQL snapshot → Java/Spring Batch → staged patches and SYSTRAN → verified mainframe handoff. In shadow mode, the flow stops at comparison.

| Item | Status | Confirming owner | Proposal |
| --- | --- | --- | --- |
| Calculation | PROPOSED | Batch engineering | Replace INTCALC.STEP15 (CBACT04C) only with Java 21 / Spring Batch. Keep input order, outputs and every OPEN quirk on the legacy option. |
| Shared inputs | PROPOSED | Mainframe data | Keep ACCTDATA, TCATBALF, CARDXREF/AIX and DISCGRP authoritative on host. Load one sealed snapshot to PostgreSQL 15; do not dual-write. |
| Account and transaction output | PROPOSED | Mainframe operations | Stage guarded ACCTDATA patches and fixed-length SYSTRAN in the target. Only the controlled host bridge can publish at cutover; shadows never do. |
| Surviving consumers | PROPOSED | Scheduler / CICS | Keep CLOSEFIL, COMBTRAN, WAITSTEP and OPENFIL; also retain POSTTRAN, disclosure refresh and online users. Gating uses observed fencing, not assumed scheduler exclusion. |

## Build steps

| Order | Step | Status | Confirming owner | Proposal |
| --- | --- | --- | --- | --- |
| 01 | Pilot | PROPOSED | Batch engineering | Read i01-baseline in order, compute interest and persist transactions/delta in an isolated PostgreSQL schema; compare against existing golden rows before expanding. |
| 02 | Schema | PROPOSED | PostgreSQL owner | Build snapshot, input, run, delta, checkpoint and publication schemas; isolate Batch metadata. Test keys, widths and permissions. |
| 03 | Fixed records | PROPOSED | Data engineering | In parallel with schema after the pilot, prove codecs, signed fields, key bytes, framing and unchanged-record round trips. |
| 04 | Full calculation | PROPOSED | Batch engineering | After schema and codec, integrate snapshot load, legacy behaviour, atomic account-group updates, and private restart and error handling. |
| 05 | Package and handoff | PROPOSED | Mainframe integration | After schema and codec, use seeded durable rows and a simulated host to verify export and fail-closed receipts. No live publish. |
| 06 | Final verification | PROPOSED | QA / release engineering | After integration, prove all fixture cases and compare RC, SYSOUT, fields and bytes; rehearse operational controls with a simulated host. Hand evidence to release owners. |

See the [ticket dependency order](tickets/README.md) for which steps can run in parallel.

## Moving the data

| Step | Status | Confirming owner | Proposal |
| --- | --- | --- | --- |
| Trial load | PROPOSED | Host data / DBA | Export one coordinated point-in-time baseline with source IDs, CCSID, lengths, key ranges and hashes; use protected or synthetic data. Byte-check framing, decode four inputs into staging, log any quarantine and prevent sealing on invalid data. |
| Reconciliation | PROPOSED | Finance control / data QA | For each input reconcile extracted = accepted + quarantined and require zero quarantine, duplicate source keys, or unexplained differences to seal. Compare by key and count: account and xref rows; distinct xref accounts and observed AIX first rows; category/rate rows per group/type/category; signed account balances, cycle credit/debit, category amounts and rate totals. Use exact decimals, counts of each sign and per-key/group sums, not just a net total. |
| Side-by-side | PROPOSED | QA / Finance | Run legacy and target on equivalent pre-run copies (pin PARM and clock for exact fixture parity). Legacy alone writes live; target writes shadow. Compare emitted count, ID/order and signed amounts by category and account, delta keys/count and before/after cycle totals, untouched final account, bytes/padding, RC and SYSOUT. Record production-only timestamp differences separately with explicit Finance acceptance, not a hidden tolerance. |
| Cutover | PROPOSED | Scheduler / CICS / mainframe operations | Hold COMBTRAN; prove POSTTRAN, CICS, refresh/setup and other conflicting activity are fenced. Use an approved one-engine release claim. Verify unchanged snapshot/preimages, publish guarded ACCTDATA patches and exactly one verified SYSTRAN(+1), confirm absolute generation resolves as (0), then signal COMBTRAN and release readers at the approved boundary. |
| Fallback | PROPOSED | Mainframe operations / release manager | Before external mutation, discard the packet and run legacy once. After partial apply/catalog and before consumption, keep the fence; use receipts, exact generation and before-images to restore and reconcile, then run legacy once only after verified restore. After consumption, hold downstream and follow incident recovery; no blind rerun or recatalog. |

There is no general ledger check that total emitted interest equals the change in all account balances: INT-Q02 deliberately emits final-account transactions without an account update; compare emitted totals and eligible account deltas separately. Explain any legacy receiver-width truncation rather than comparing unrestricted sums.

## Review boundaries

| Item | Status | Confirming owner | Proposal |
| --- | --- | --- | --- |
| Legacy calculation remains the reference | PROPOSED | Finance / Card Operations | All QUIRK and HOLE rows remain OPEN; design uses legacy option A where known. The decision register holds alternatives. Unknown host input or restart evidence stays a release constraint, not a new business ticket. |
| Production proof is outstanding | PROPOSED | Mainframe data / Scheduler / Release | Confirm actual CCSID/framing, AIX choice, generation/restore procedures, fence and binder provenance. No production release approval follows from lab parity. |

Read [INT-ARCH v1.0](architecture.md) for table ownership, transaction boundaries, fence and recovery; [INT-MAP v1.0](data-map-interest.md) for field mapping; the [decision register](../rules/interest/decisions.md) for OPEN business/recovery options; and the [stream manifest](../streams/interest/manifest.json) for production evidence asks. No implementation, merge or deployment is authorized by this proposal.
