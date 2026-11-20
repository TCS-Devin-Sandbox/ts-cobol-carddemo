# CardDemo batch on .NET 8

The five CardDemo batch workloads are ported from COBOL/JCL to .NET 8 / C# 12 here. The work followed the
batch-to-.NET modernization plugin phase by phase, and every workload is proven against the legacy run.

| Workload | Legacy | .NET | Proof |
|---|---|---|---|
| READACCT | CBACT01C + COBDATFT (HLASM) | `Programs/ReadPrograms.cs` `Cbact01c`, `CobDatFt` | unit tests + sample run (GAP-001) |
| READCARD | CBACT02C | `Programs/ReadPrograms.cs` `Cbact02c` | sample run |
| INTCALC | CBACT04C | `Programs/Cbact04c.cs` | 7 golden fixtures |
| POSTTRAN | CBTRN02C | `Programs/Cbtrn02c.cs` | 7 golden fixtures |
| TRANREPT | REPRO + DFSORT + CBTRN03C | `Programs/Utilities.cs`, `Programs/Cbtrn03c.cs` | 7 golden fixtures |

## Run

```sh
./run_modernization.sh                      # all 8 phases and the 3 gates
tools/parity_dotnet.sh                      # .NET target vs fixtures/*/expected (repository comparator)
CARDDEMO_PG="Host=<db>;Username=carddemo;Password=carddemo;Database=carddemo_net" tools/parity_dotnet.sh  # KSDS on PostgreSQL
dotnet test CardDemo.Batch.sln
dotnet src/CardDemo.Host/bin/Release/net8.0/carddemo-batch.dll run-chain posting ../fixtures/posting/p01-baseline-mixed ../work/run [--restart]
```

PostgreSQL tables come from `db/migrations/001_create_vsam_tables.sql`, which is generated from the copybooks.

## Layout

- `src/CardDemo.Domain`: generated copybook layouts, the COBOL numeric codec and `[Trace]`.
- `src/CardDemo.Data`: the VSAM keyed store (with AIX), the PostgreSQL and file persistence, QSAM files, and the GDG catalog.
- `src/CardDemo.Batch`: one class per COBOL program, plus the SORT and IDCAMS utilities and the chunk checkpoints.
- `src/CardDemo.Orchestration`: the JCL job catalog, the job runner (return codes, abends, the run ledger, restart) and the chain runner.
- `src/CardDemo.Host`: the `carddemo-batch` command-line tool.
- `tests/CardDemo.Tests`: codec unit tests, golden-fixture contract tests and the restart test.
- `Artefacts/`: Discovery, Planning (IR files and ADRs), Review (gates 1 and 2), Reports (parity and 5-level reconciliation), and Audits (gate 3 and the traceability matrix).
- `tools/`: the phase scripts (discovery, codegen, planning, review, validation, audit).
