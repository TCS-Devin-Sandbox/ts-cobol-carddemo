# READCARD — Implementation Processing Flow

## Execution sequence

```
main()
 ├─ build_context()          S0  args + config.json → RunContext (metrics seeded)
 ├─ create_spark()           S0  SparkSession from config.spark
 └─ run()
     ├─ listing ← START banner                        BR-004
     ├─ read_source()        S1  path_exists → read_fixed_width | read_parquet   (open error → E)
     ├─ raw.count()              input_records
     ├─ validate_records()   S2  V-01 length, V-02 unique key                     (read error → E)
     ├─ apply_transformations() S2 rpad 150 + field projection
     ├─ apply_business_rules()  S3 sort_key (EBCDIC) → orderBy
     ├─ generate_output()    S4  temp listing: banner + ordered record_image lines, sha256/min/max
     ├─ finalize()           S5  + END banner, atomic os.replace                  (close error → E)
     └─ write_outcome(0)         rc.json, run_metrics.json
     E: handle_abend()           listing so far + error_lines() → rc.json(abend_rc), metrics, exit abend_rc
```

## Module relationships

| Function | Depends on | Produces |
|---|---|---|
| build_context | load_config | RunContext |
| read_source | path_exists, read_fixed_width, read_parquet | card_master_raw (SCH-03) |
| validate_records | config.layout, config.legacy_file_status | — / CardFileError |
| apply_transformations | config.layout | card_master (SCH-04) |
| apply_business_rules | ebcdic_translation | card_master_ordered |
| generate_output | — | temp listing, metrics |
| finalize | — | sysout/READCARD.STEP05.txt (SCH-05) |
| write_outcome | — | rc.json (SCH-06), run_metrics.json (SCH-07) |
| handle_abend | error_lines, format_file_status, write_outcome | error listing, rc/metrics |

## Invocation

```
python pyspark_application.py --config config.json --input <dir> --out <dir> [--chain read-masters --case <case> --clock <ts>]
spark-submit pyspark_application.py --config config.json --input <dir> --out <dir>
```
Parity harness: `CANDIDATE="<python> workbench/pyspark_generation/pyspark_application.py" python tools/parity/run_candidate.py --chain read-masters --steps READCARD`.

## Output handling
- Write mode: overwrite (atomic temp + rename) for the listing; rc/metrics overwritten.
- Partitioning: none — one ordered listing file (design S4); Spark parallelises read, validation and sort.
- Schema enforcement: RAW_SCHEMA on ingest; fixed 150-char record_image; layout from config.
