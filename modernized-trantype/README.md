# modernized-trantype

Modernization of the CardDemo DB2 transaction-type module (`app/app-transaction-type-db2/`): CICS transactions **CTLI** (`COTRTLIC`, list/update/delete) and **CTTU** (`COTRTUPC`, add/edit/delete), rebuilt as a Spring Boot REST API plus a React UI on PostgreSQL 15.

| Path | Content |
|---|---|
| `docs/discovery-ctli-cttu.md` | Phase 1: paragraphs, perform graphs, BMS fields, SQL, behaviour, messages, traceability |
| `docs/requirements-ctli-cttu.md` | Phase 2: numbered requirements + RTM |
| `db/migration/V1__create_tables.sql`, `V2__seed_data.sql` | Phase 3: Flyway schema + CREADB21 seed |
| `docs/schema-mapping.md` | Phase 3: DB2 → PostgreSQL mapping |
| `docs/target-spec.md` | Phase 4: OpenAPI, DTOs, error mapping, auth, UI spec |
| `carddemo-trantype-api/` | Phase 5: Spring Boot 3 / Java 17 API |
| `carddemo-trantype-ui/` | Phase 5: React 18 + TypeScript + MUI UI |
| `docs/test-plan.md`, `docs/deviations.md` | Phase 6: test matrix + results, deviations |

## Prerequisites

Java 17, Maven 3.9+, Node 20+ (npm), Docker (PostgreSQL and Testcontainers).

## Run locally

```bash
# 1. PostgreSQL 15
docker compose up -d postgres

# 2. API on :8080 (Flyway creates and seeds the tables)
cd carddemo-trantype-api
mvn spring-boot:run

# 3. UI on :5173 (proxies /api to :8080)
cd ../carddemo-trantype-ui
npm install
npm run dev
```

Open http://localhost:5173 and sign on as `ADMIN001` / `PASSWORD` (admin). `USER0001` / `PASSWORD` is a regular user and is refused (`No access - Admin Only option... `).

Configuration (env vars): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CARDDEMO_JWT_SECRET` (≥ 32 bytes, required outside development), `PORT`. UI: `VITE_API_BASE_URL` (default same origin) or `VITE_API_PROXY` for the dev proxy target.

## Build and test

```bash
cd carddemo-trantype-api && mvn verify      # JUnit 5 + Mockito unit tests, Testcontainers integration tests (*IT)
cd carddemo-trantype-ui  && npm test && npm run build   # Vitest + React Testing Library, type check, bundle
```

## API quick reference

```bash
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"userId":"ADMIN001","password":"PASSWORD"}' | jq -r .token)
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/transaction-types?size=7'
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/transaction-types?direction=next&cursor=08'
curl -s -H "Authorization: Bearer $TOKEN" -X POST localhost:8080/api/transaction-types \
  -H 'Content-Type: application/json' -d '{"trType":"8","trDescription":"FEE ASSESSMENT"}'
curl -s -H "Authorization: Bearer $TOKEN" -X DELETE localhost:8080/api/transaction-types/01   # 409 FK_RESTRICT
```

Out of scope: the VSAM/sequential copies (`TRANEXTR`), batch maintenance (`MNTTRDB2`/`COBTUPDT`) and the rest of the admin menu. See `docs/deviations.md`.
