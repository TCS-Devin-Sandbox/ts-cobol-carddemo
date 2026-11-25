# READCARD — Business Capabilities

Only capabilities directly evidenced by the artifacts.

## Business capabilities

| Capability | Evidence | Rules |
|---|---|---|
| Reporting — card master listing | CBACT02C header "Read and print card data file"; DISPLAY CARD-RECORD | BR-001..BR-004 |
| Card master data verification (batch read-back of the master after load) | Member of `read-masters` chain (`tools/runjcl/chains.json`), runs after CARDFILE setup; fixtures r01/r02 | BR-001, BR-005 |

Not supported (no evidence): card issuance, maintenance, validation of card fields, account/transaction processing.

## Supported processes

| Process | Role of READCARD |
|---|---|
| Card master load (CARDFILE.jcl) | Downstream read-back / listing |
| Batch operations monitoring | Banner lines and U999 abend signal run outcome |

## Business functions

| ID | Function | Rules |
|---|---|---|
| BF-01 | List all card records in card-number order, verbatim | BR-001, BR-002, BR-003, BR-004 |
| BF-02 | Detect and stop on file I/O failure | BR-006, TR-002, TR-004 |
| BF-03 | Report file status for diagnostics | TR-003 |

## Operational objectives

- Produce a complete, ordered, unmodified listing of the card master (BF-01).
- Never alter card data (TR-005).
- Signal failure unambiguously (U999) and succeed on an empty master (BR-005, BR-006).
