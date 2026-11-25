# READCARD validation assets

```sh
python test_data/generate_test_data.py          # synthetic data sets
cd workbench/validation && python -m pytest -rA # features/READCARD.feature via steps/READCARD_steps.py
CANDIDATE="python workbench/pyspark_generation/pyspark_application.py" \
  python tools/parity/run_candidate.py --chain read-masters --steps READCARD   # golden parity (repo root)
```
Pipeline: generate test data → run the PySpark job → run the BDD scenarios → collect `junit_results.xml` → reports. Requires pyspark 3.5, pytest-bdd 8.1 and Java 17 or later.
Parallel run: compare legacy SYSOUT/rc.json with `compare.py`, and legacy line counts/SHA-256 with `run_metrics.json` (see `reconciliation_report.md`).
