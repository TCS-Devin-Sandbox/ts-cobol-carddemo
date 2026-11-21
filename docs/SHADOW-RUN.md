# Nightly shadow comparison (F3, in review)

This is an offline comparison, not a production scheduler or cutover approval.
The nightly GitHub Actions workflow uses synthetic fixtures only. A real feed
must be supplied by the mainframe operator; no production connection is inferred.
Covered F2 chains: posting, interest, read-masters, trantype-update and
trantype-dynamic. Statements is not under F2 and is intentionally excluded.

## Drop contract

Publish an immutable, complete directory atomically after both unload and run:

```
<drop>/<chain>/<case>/unload/manifest.json
<drop>/<chain>/<case>/unload/<input-dataset-name>.txt
<drop>/<chain>/<case>/unload/INPFILE.txt       # where applicable
<drop>/<chain>/<case>/unload/DYNSQL.txt        # dynamic maintenance
<drop>/<chain>/<case>/unload/db/seed.sql      # optional synthetic/approved seed
<drop>/<chain>/<case>/mainframe/manifest.json
<drop>/<chain>/<case>/mainframe/datasets/...
<drop>/<chain>/<case>/mainframe/sysout/...
<drop>/<chain>/<case>/mainframe/rc.json
<drop>/<chain>/<case>/mainframe/db/...
```

VSAM input names must be the names expected by the chain (see
`tools/runjcl/chains.json`). Despite the `.txt` suffix, the files listed in the
F1 manifest are fixed, unblocked IBM037 unloads, including binary numeric bytes,
without newline/RDW/BDW. Manifest entries are F1 `{file, dataset}` objects;
see [F1](../java/data-load/README.md) for datasets, widths and constraints.
Every VSAM input must be listed. Non-VSAM sequential inputs and database seeds
are passed through unchanged; they must already be in the Java launcher's text
format. Seeds are executable SQL: accept drops only from trusted operators.
The trantype chains have no VSAM inputs: their manifest is `[]` and reconciliation
is `NOT_APPLICABLE`, not a fictitious F1 MATCH.
Posting's `DALYTRAN.PS.INIT.txt` is the existing transaction master unload;
`DALYTRAN.PS.txt` is the sequential daily posting feed and is passed through.
An output manifest may be omitted for trantype, which has no dataset snapshots,
matching the existing recorded format.

`mainframe/` uses exactly the recorded `fixtures/*/*/expected` format: complete
output manifest with layouts/counts, LF-delimited dataset snapshots, SYSOUT,
return-code JSON, and ordered CSV database snapshots. Convert the mainframe
outputs into this format before publishing. The comparator uses the manifest
as the comparison scope; completeness is a feed-owner responsibility. Align
input cut, job PARMs, and business date/clock with the chain's configured clock
in `chains.json` (currently frozen for this lab). Arbitrary production dates
are not yet a supported feed.

## Run

Requires Python 3.10+, JDK 21, Maven, Docker Compose and PostgreSQL 15:

```sh
make shadow                         # synthetic drop built from fixtures
make shadow DROP=/secure/drop RUN_ID=shadow_20260705
make shadow RUN_ID=shadow_negative NEGATIVE_PROOF=1
```

The default run ID is `shadow_<UTC yyyymmdd>`. A case-specific deterministic
suffix isolates F1/F2 schemas and the run ID of the fixture-backed launchers.
F1 loads into a private schema, reconciles, then the existing B4 `VsamTransfer`
exporter writes the chain's text inputs from PostgreSQL. No independent codec
or fixture shortcut supplies those VSAM inputs. Synthetic drops use the same
Latin-1 decode / IBM037 encode as `DataLoadTest`, copying sequential files and
expected outputs unchanged. Negative proof copies the drop privately and appends
a synthetic SYSOUT line; it must produce DRIFT and nonzero exit.

The fixture-backed F2 launchers receive `--run-id`; trantype-batch receives
`run.id` and runs in a private database initialized with the existing DDL and
optional seed. See [restart boundaries](BATCH-RESTART.md). This small orchestrator
deliberately refuses to overwrite an existing case directory: for manual F2
recovery retain its inputs/output/database and use the runbook directly, or
start a new shadow run ID. Do not run two shadow commands concurrently in the
same checkout. Run schemas, trantype databases and artifacts are retained for
inspection; the operator must retire them after the retention window.

Compose manages the local `postgres` service. `SHADOW_JDBC_URL`, `PGUSER` and
`PGPASSWORD` can override the F1/VSAM-chain database connection; trantype uses
the local compose database and lab credentials. Remote trantype deployments
require external provisioning and are outside this wrapper.

## Evidence and exit codes

`work/shadow/<run>/drift.md` and `drift.json` summarize each chain/case:
F1 reconciliation status, chain process exit, MATCH/DRIFT/ERROR and comparator
difference rows (`where`, `field`, `expected`, `actual`). Full F1 reconciliation
is under each case's `reconciliation/`; `comparisons.md` contains normal parity
counts and field-level reports. Overall MATCH requires at least one case and
every case to MATCH. Exit **0** = MATCH; **1** = DRIFT or case error;
**2** = CLI usage error. Build/setup failure is also nonzero and may precede
report creation. A missing/empty drop cannot yield MATCH.

The backwards-compatible comparator override is also available directly:
`python3 tools/parity/compare.py CHAIN CASE --actual DIR --expected MAINFRAME_DIR`.
Without `--expected`, all existing parity commands still use golden fixtures.
Reports include decoded differences; restrict access and retention for real data.
The Actions job uploads reports even after failure, never the drop itself.
