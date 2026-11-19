# INTCALC strategy page check

Tested the offline file URL in Chrome on the VM using mouse and keyboard. Started at the overview with **Move map** selected. Tested HTML SHA-256: `a74e507b1d1682f8ce9124ad3a7eee0bc118194b9de2c8ab04957c8da6bb6bde`.

| Check | Result | Observation |
|---|---|---|
| File and default view | PASS | Exact file URL; Move map selected at overview. |
| Console and assets | PASS | Cache-disabled offline reload: one successful local HTML load; no external asset requests, failed loads or console errors. |
| In-page navigation | PASS | Three links reached Migration plan, Review boundaries, and Back to overview. |
| Tabs and focus | PASS | All three panels switched via mouse and keyboard; focus followed keyboard selection. |
| Phase filter | PASS | All/Foundation/Integration/Gate showed 6/3/2/1; reset restored six. |
| Collapsibles | PASS | All five operations opened and closed. |
| Diagrams | PASS | Both inline SVGs rendered labelled nodes and arrows. |
| Counts | PASS | Displayed 5 copybooks, 7 fixture cases and 15 OPEN decisions; matched embedded data and independently checked sources. |
| Baseline SHAs | PASS | Both full SHAs displayed: design `0dd0a8e672cbdf46479b45b02a5ba8c650de6561` and estate `41519206b887cd0123ea850b46a96cdc9baa69af`. |

[Final overview screenshot](strategy-interest-final.png). This is page and design-document evidence; it does not claim Java, candidate parity or live host verification.
