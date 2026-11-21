#!/usr/bin/env bash
set -euo pipefail

# Called by tools/parity/run_candidate.py from the repository root.
while (( $# )); do
    case "$1" in
        --input) input=$2 ;;
        --out) out=$2 ;;
        --chain) chain=$2 ;;
        --case|--clock) ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
    shift 2
done
: "${input:?Missing --input}" "${out:?Missing --out}"

job_args=("input=$input/INPFILE.txt" "out=$out")
case "${chain:?Missing --chain}" in
    trantype-update) ;;
    trantype-dynamic) job_args+=(--spring.batch.job.name=MNTTRDYN "dynsql=$input/DYNSQL.txt" parm=COBTUPDT) ;;
    *) echo "Unsupported chain: $chain" >&2; exit 2 ;;
esac

read -ra compose <<< "${COMPOSE:-docker compose}"
db_exec() {
    "${compose[@]}" exec -T postgres "$@"
}
psql_fixture() {
    db_exec psql -Xq -v ON_ERROR_STOP=1 -U carddemo -d "$database" "$@"
}

database="trantype_parity_$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
db_exec createdb -U carddemo "$database"
trap 'db_exec dropdb -U carddemo "$database"' EXIT
psql_fixture < db/postgres/01-transaction-type.sql
if [[ -f $input/db/seed.sql ]]; then
    psql_fixture < "$input/db/seed.sql"
fi

SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/$database" \
SPRING_DATASOURCE_USERNAME=carddemo SPRING_DATASOURCE_PASSWORD=carddemo \
    java -jar java/trantype-batch/target/trantype-batch.jar \
    --spring.batch.jdbc.initialize-schema=always "${job_args[@]}"

mkdir -p "$out/db"
psql_fixture -c 'COPY (SELECT * FROM carddemo.transaction_type ORDER BY tr_type) TO STDOUT WITH CSV HEADER' \
    > "$out/db/carddemo.transaction_type.csv"
psql_fixture -c 'COPY (SELECT * FROM carddemo.transaction_type_categor ORDER BY trc_type_code, trc_type_category) TO STDOUT WITH CSV HEADER' \
    > "$out/db/carddemo.transaction_type_categor.csv"
