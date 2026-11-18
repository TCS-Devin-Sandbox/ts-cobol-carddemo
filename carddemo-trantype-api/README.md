# carddemo-trantype-api

Spring Boot 3 / Java 17 replacement for CICS transactions CTLI (COTRTLIC) and CTTU (COTRTUPC). Contract: `../docs/target-spec.md`.

## Run
```sh
docker compose -f ../docker-compose.yml up -d postgres
mvn spring-boot:run
```
Flyway applies `../db/migration` (packaged into the jar as `classpath:db/migration`).

| Env var | Default |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/carddemo` / `carddemo` / `carddemo` |
| `JWT_SECRET` | dev-only value; set ≥32 bytes in any shared environment |
| `ADMIN_PASSWORD` / `USER_PASSWORD` | `admin123` / `user123` |

```sh
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/transaction-types?size=7'
```

## Test
`mvn test` — unit tests (JUnit 5, Mockito, MockMvc) and `TransactionTypeApiIntegrationTest` (`@SpringBootTest` + Testcontainers `postgres:15-alpine`; requires Docker).
