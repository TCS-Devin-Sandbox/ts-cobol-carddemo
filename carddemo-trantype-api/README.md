# carddemo-trantype-api

Spring Boot 3 / Java 17 REST API replacing CICS transactions **CTLI** (`COTRTLIC`) and **CTTU** (`COTRTUPC`). Contract: `../docs/target-spec.md`.

## Prerequisites

- Java 17, Docker (PostgreSQL via Compose and Testcontainers). Maven is provided by the wrapper (`./mvnw`).

## Build and test

```bash
./mvnw verify          # unit tests (surefire) + Testcontainers PostgreSQL 15 integration tests (failsafe, *IT)
./mvnw test            # unit tests only
```

## Run

```bash
docker compose -f ../docker-compose.yml up -d   # PostgreSQL 15 on localhost:5432 (db/user/password: carddemo)
./mvnw spring-boot:run                          # http://localhost:8080, Flyway applies ../db/migration V1 + V2
```

Configuration (environment variables): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT`, `CARDDEMO_ADMIN_USER` / `CARDDEMO_ADMIN_PASSWORD` (default `admin` / `admin123`, role ADMIN), `CARDDEMO_USER_USER` / `CARDDEMO_USER_PASSWORD` (default `user` / `user123`, role USER). Change the defaults outside local development.

Flyway migrations are not duplicated: the build copies `../db/migration/*.sql` to `classpath:db/migration`.

## Quick check with curl

```bash
curl -s -c cj -b cj localhost:8080/api/auth/csrf            # sets XSRF-TOKEN cookie, returns token
curl -s -c cj -b cj -H "X-XSRF-TOKEN: <token>" -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"admin123"}' localhost:8080/api/auth/login
curl -s -b cj 'localhost:8080/api/transaction-types?size=7'
```

## Layout

| Package | Content |
|---|---|
| `domain` | `TransactionType`, `TransactionTypeCategory` (+ id) JPA entities |
| `repository` | keyset queries (`findForward` / `findBackward`), native UPDATE / INSERT / DELETE mirroring the COBOL SQL |
| `service` | `TransactionTypeService` (paging, get, create, upsert, delete), source message literals, `ApiException` |
| `validation` | `@TranTypeCode` constraint (1245-EDIT-NUM-REQD) |
| `web` | controller, request/response DTOs, `ApiExceptionHandler` (SQLSTATE → SQLCODE → HTTP) |
| `security` | session-based Spring Security, `/api/auth/*` |
