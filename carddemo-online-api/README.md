# carddemo-online-api

Spring Boot 3 / Java 17 API for CardDemo's online transactions. It currently serves CTLI (COTRTLIC) and CTTU (COTRTUPC); see [the transaction-type contract](../docs/trantype/target-spec.md).

## Run
```sh
docker compose -f ../docker-compose.yml up -d postgres
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run   # dev profile supplies local-only JWT secret and passwords
```
Flyway keeps the existing DB2-derived definitions and seed data from `../db/migration`. It applies the [shared VSAM master migrations](../java/common/src/main/resources/db/migration/) in the same schema (`public` by default), using a separate `flyway_vsam_schema_history` table so the two migration sequences do not collide. Transaction-type endpoints retain their existing tables and data.

| Env var | Default |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/carddemo` / `carddemo` / `carddemo` |
| `JWT_SECRET` | **required**, ≥32 bytes (startup fails otherwise); `dev` profile only: fixed local value |
| `ADMIN_PASSWORD` / `USER_PASSWORD` | **required**; `dev` profile only: `admin123` / `user123` |

```sh
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/transaction-types?size=7'
```

## Test
`mvn test` — unit tests (JUnit 5, Mockito, MockMvc) and `TransactionTypeApiIntegrationTest` (`@SpringBootTest` + Testcontainers `postgres:15-alpine`; requires Docker).
