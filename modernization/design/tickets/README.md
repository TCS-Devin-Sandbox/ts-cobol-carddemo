# INTCALC engineering queue — INT-ARCH v1.0

**PROPOSED · Not started.** Design source: [architecture](../architecture.md), [data map](../data-map-interest.md), [strategy](../strategy-interest.md). Start only after human design review. Preserve legacy option A of each OPEN decision where known; alternatives and owner decisions live in the [decision register](../../rules/interest/decisions.md), not this queue.

| Wave | Work item | Waits for | Result |
|---|---|---|---|
| 1 | [INT-01 — **Pilot**](01-pilot.md) | Nothing | Read, compute and write i01 end to end; compare against goldens. |
| 2 | [INT-02 — Schema](02-schema.md) | INT-01 | Versioned PostgreSQL and Batch metadata contracts. |
| 2 | [INT-03 — Record codecs](03-record-codecs.md) | INT-01 | Fixed-record codec; does not wait for schema. |
| 3 | [INT-04 — Calculation and private restart](04-calculation-restart.md) | INT-02 and INT-03 | Sealed input, full calculation and staged results. |
| 3 | [INT-05 — Simulated shadow handoff](05-shadow-handoff.md) | INT-02 and INT-03 | Deterministic package and fail-closed bridge state. |
| 4 | [INT-06 — Final technical gate](06-parity-gate.md) | INT-04 and INT-05 | All-case parity, DB reconciliation and simulated release evidence. |

INT-02 and INT-03 can start in parallel once the pilot supplies the shared module. INT-04 and INT-05 both wait for the schema and codec; they can start in parallel because INT-05 seeds the documented schema and does not need INT-04's implementation. The final technical gate is not a release decision: live owner sign-offs and evidence stay outside the queue. All work items are Markdown specifications only. No external tickets or implementation sessions were created.
