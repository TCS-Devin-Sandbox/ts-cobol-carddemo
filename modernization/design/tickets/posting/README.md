# Posting queue — Track A

**PROPOSED · Not started.** Plan: [tracks.md](../../tracks.md). Hand-off: [posting](../../../streams/posting/handoff.md). Frozen: [shared-layouts.md](../../shared-layouts.md).

Ticket writes: prohibited

| Wave | Work item | Waits for | Result |
|---|---|---|---|
| 1 | [POST-01 — Pilot thin slice](POST-01-pilot.md) | F0 freeze | p01 posted by Java CBTRN02C and reported by Java CBTRN03C |
| 2 | [POST-02 — CBTRN02C rest](POST-02-cbtrn02c.md) | POST-01 | POSTTRAN outputs p01–p07 |
| 2 | [POST-03 — CBTRN03C rest](POST-03-cbtrn03c.md) | POST-01 | Report p01–p07 |
| 3 | [POST-04 — Posting gate](POST-04-gate.md) | POST-02, POST-03 | 7/7 with both Java programs |
