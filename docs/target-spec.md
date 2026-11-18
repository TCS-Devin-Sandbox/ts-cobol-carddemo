# Target specification — `carddemo-trantype-api` + `carddemo-trantype-ui`

Implements `docs/requirements-ctli-cttu.md`. Schema: `docs/schema-mapping.md`. Deviations: `docs/deviations.md`.

## 1. Architecture

```
Browser (React 18 SPA, MUI, React Router)  ──/api (same origin; Vite proxy in dev)──►  Spring Boot 3 API (Java 17)
  LoginPage / AdminMenuPage (CA00) /                                                     Spring Security (session, ROLE_ADMIN)
  TransactionTypeListPage (CTLI) /                                                       TransactionTypeController
  TransactionTypeEditPage (CTTU)                                                         TransactionTypeService (keyset paging, upsert)
                                                                                         Spring Data JPA repositories ─► PostgreSQL 15 (Flyway V1, V2)
```

Pseudo-conversational CICS state (COMMAREA) becomes: list paging keys (`cursor`, `direction`, `page`) passed on every request; CTTU's `TTUP-CHANGE-ACTION` lives in the React page state. Each HTTP request is one database transaction (≙ SYNCPOINT).

## 2. Authentication / authorisation (decision: server-side session)

- **Choice: Spring Security session (JSESSIONID cookie)**, not JWT. Rationale: CICS sign-on establishes a terminal session for the user; a session cookie gives the same semantics (logout ends it, no token storage in the browser), and the SPA is served from the same origin.
- `GET /api/auth/csrf` → `{token, headerName}` and sets `XSRF-TOKEN` cookie; all state-changing calls send `X-XSRF-TOKEN` (axios does this automatically).
- `POST /api/auth/login` `{username, password}` → 200 `{username, roles}` | 401 `{code:"UNAUTHORIZED", message:"Wrong Password. Try again ..."}`.
- `POST /api/auth/logout` → 204. `GET /api/auth/me` → 200 `{username, roles}` | 401.
- Users: in-memory, configured by properties (`carddemo.security.admin-*`, `carddemo.security.user-*`), passwords BCrypt-hashed at start-up. Passwords are mandatory (start-up fails when empty); the `local` profile alone provides development defaults. Role `ADMIN` ≙ CardDemo user type `A`; role `USER` ≙ type `U`.
- `/api/transaction-types/**` requires `ROLE_ADMIN`: anonymous → 401 `UNAUTHORIZED` 'Please sign on to use this function'; authenticated non-admin → 403 `FORBIDDEN` 'Access restricted to administrators'.

## 3. REST API (OpenAPI 3.0)

```yaml
openapi: 3.0.3
info:
  title: CardDemo Transaction Type API (CTLI/CTTU)
  version: 1.0.0
servers:
  - url: http://localhost:8080
security:
  - sessionCookie: []
paths:
  /api/transaction-types:
    get:
      operationId: listTransactionTypes
      summary: Keyset page of transaction types (CTLI 8000-READ-FORWARD / 8100-READ-BACKWARDS)
      parameters:
        - {name: cursor, in: query, required: false, schema: {type: string, maxLength: 2},
           description: "forward: inclusive start key (TR_TYPE >= cursor), default '' = first page; backward: exclusive upper key (TR_TYPE < cursor) = firstKey of current page"}
        - {name: direction, in: query, required: false, schema: {type: string, enum: [forward, backward], default: forward}}
        - {name: size, in: query, required: false, schema: {type: integer, minimum: 1, maximum: 7, default: 7}}
        - {name: page, in: query, required: false, schema: {type: integer, minimum: 1, default: 1},
           description: "page number to report for the returned page (WS-CA-SCREEN-NUM); client sends current+1 / current-1"}
        - {name: typeFilter, in: query, required: false, schema: {type: string, maxLength: 2},
           description: "blank or '00' = none; else exactly 2 digits, exact match"}
        - {name: descFilter, in: query, required: false, schema: {type: string, maxLength: 50},
           description: "blank = none; else TR_DESCRIPTION LIKE '%trim(x)%' (case-sensitive)"}
      responses:
        '200': {description: page, content: {application/json: {schema: {$ref: '#/components/schemas/TransactionTypePage'}}}}
        '400': {$ref: '#/components/responses/Error'}
        '401': {$ref: '#/components/responses/Error'}
        '403': {$ref: '#/components/responses/Error'}
        '500': {$ref: '#/components/responses/Error'}
  /api/transaction-types/{trType}:
    parameters:
      - {name: trType, in: path, required: true, schema: {type: string, pattern: '^\s*\d{1,2}\s*$'},
         description: "1-2 digit, non-zero; normalised to 2 digits (1210-EDIT-TRANTYPE)"}
    get:
      operationId: getTransactionType
      summary: CTTU 9100-GET-TRANSACTION-TYPE
      responses:
        '200': {description: found, content: {application/json: {schema: {$ref: '#/components/schemas/TransactionType'}}}}
        '400': {$ref: '#/components/responses/Error'}
        '404': {$ref: '#/components/responses/Error'}
    post:
      operationId: createTransactionType
      summary: Plain INSERT (CTTU 9700-INSERT-RECORD)
      requestBody: {required: true, content: {application/json: {schema: {$ref: '#/components/schemas/TransactionTypeRequest'}}}}
      responses:
        '201': {description: created, content: {application/json: {schema: {$ref: '#/components/schemas/TransactionType'}}}}
        '400': {$ref: '#/components/responses/Error'}
        '409': {$ref: '#/components/responses/Error'}
    put:
      operationId: saveTransactionType
      summary: "upsert=true (default): CTTU 9600 UPDATE then INSERT on +100; upsert=false: CTLI 9200 UPDATE only"
      parameters:
        - {name: upsert, in: query, required: false, schema: {type: boolean, default: true}}
      requestBody: {required: true, content: {application/json: {schema: {$ref: '#/components/schemas/TransactionTypeRequest'}}}}
      responses:
        '200': {description: updated, content: {application/json: {schema: {$ref: '#/components/schemas/TransactionType'}}}}
        '201': {description: inserted (upsert), content: {application/json: {schema: {$ref: '#/components/schemas/TransactionType'}}}}
        '400': {$ref: '#/components/responses/Error'}
        '404': {$ref: '#/components/responses/Error'}
        '409': {$ref: '#/components/responses/Error'}
    delete:
      operationId: deleteTransactionType
      summary: CTLI 9300-DELETE-RECORD / CTTU 9800-DELETE-PROCESSING
      responses:
        '204': {description: deleted}
        '400': {$ref: '#/components/responses/Error'}
        '404': {$ref: '#/components/responses/Error'}
        '409': {$ref: '#/components/responses/Error'}
components:
  securitySchemes:
    sessionCookie: {type: apiKey, in: cookie, name: JSESSIONID}
  responses:
    Error: {description: error, content: {application/json: {schema: {$ref: '#/components/schemas/Error'}}}}
  schemas:
    TransactionType:
      type: object
      required: [trType, description]
      properties:
        trType: {type: string, minLength: 2, maxLength: 2, example: '01'}
        description: {type: string, maxLength: 50, example: PURCHASE}
    TransactionTypeRequest:
      type: object
      required: [description]
      properties:
        description: {type: string, maxLength: 50, pattern: '^[A-Za-z0-9 ]*$'}
    TransactionTypePage:
      type: object
      properties:
        rows: {type: array, maxItems: 7, items: {$ref: '#/components/schemas/TransactionType'}}
        firstKey: {type: string, nullable: true, description: WS-CA-FIRST-TR-CODE}
        lastKey: {type: string, nullable: true, description: key of last displayed row}
        nextCursor: {type: string, nullable: true, description: "look-ahead key (WS-CA-LAST-TR-CODE after 8th fetch); use with direction=forward"}
        hasNext: {type: boolean, description: CA-NEXT-PAGE-EXISTS}
        hasPrev: {type: boolean}
        pageNumber: {type: integer, description: WS-CA-SCREEN-NUM}
        message: {type: string, nullable: true, description: "source literal when no rows"}
    Error:
      type: object
      required: [code, message]
      properties:
        code: {type: string, enum: [VALIDATION_ERROR, NOT_FOUND, DUPLICATE_KEY, CHILD_RECORDS_EXIST, LOCK_ERROR, UNAUTHORIZED, FORBIDDEN, INTERNAL_ERROR]}
        message: {type: string, description: verbatim COBOL literal}
        sqlcode: {type: integer, nullable: true, description: DB2-equivalent SQLCODE for SQL failures (100, -532, -803, -911, -1)}
```

### 3.1 Keyset algorithm (service)

| Request | SQL (JPQL with source-style filter flags) | Result |
|---|---|---|
| forward, cursor `c` (default `''`) | `tr_type >= c AND filters ORDER BY tr_type LIMIT size+1` | rows = first `size`; `hasNext` = got `size+1`; `nextCursor` = row `size+1` key; no rows from a non-empty `c` → first page (D-07) |
| backward, cursor `c` | `tr_type < c AND filters ORDER BY tr_type DESC LIMIT size`, reversed | if fewer than `size` rows exist before `c` → return the first page (page 1) instead (deviation D-07); `hasNext` / `nextCursor` via forward look-ahead from `lastKey` |
| any | `hasPrev` = exists `tr_type < firstKey AND filters` | |
| no rows | `message` = 'No Records found for these filter conditions' when a filter is active (1290 COUNT=0), else 'No records found for this search condition.' (8000) | `pageNumber` = 1 |

Filter flags mirror the cursor predicate `((:WS-EDIT-TYPE-FLAG='1' AND TR_TYPE=:filter) OR :flag<>'1')`.

### 3.2 Validation (Bean Validation, identical literals)

| Input | Constraint | Message (source) |
|---|---|---|
| `{trType}` blank | `@TranTypeCode` | 'Tran Type code must be supplied.' |
| `{trType}` not 1–2 digits | `@TranTypeCode` | 'Tran Type code must be numeric.' |
| `{trType}` = 0 / 00 | `@TranTypeCode` | 'Tran Type code must not be zero.' |
| `description` null/blank | `@NotBlank` | 'Transaction Desc must be supplied.' |
| `description` chars ∉ `[A-Za-z0-9 ]` | `@Pattern` | 'Transaction Desc can have numbers or alphabets only.' |
| `description` > 50 | `@Size(max=50)` | 'Transaction Desc can have numbers or alphabets only.' (BMS length 50 prevents this on 3270; D-04) |
| `typeFilter` not blank/00 and not 2 digits | service | 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' |

Descriptions are trimmed before storage (9200/9600 `FUNCTION TRIM`). The first violated rule in the order above is reported (COBOL `WS-RETURN-MSG-OFF` keeps the first message).

### 3.3 Error mapping (`@RestControllerAdvice`)

| Condition | Source | HTTP | code | message | sqlcode |
|---|---|---|---|---|---|
| Bean/edit validation | 1210/1245/1230/1240/1220 | 400 | VALIDATION_ERROR | edit literal | – |
| GET key not found | 9100 +100 | 404 | NOT_FOUND | 'No record found for this key in database' | 100 |
| PUT upsert=false row missing | 9200 +100 | 404 | NOT_FOUND | 'Record not found. Deleted by others ? ' | 100 |
| DELETE row missing | 9300/9800 OTHER (+100) | 404 | NOT_FOUND | 'Delete failed with message:' | 100 |
| FK restrict on delete (`DataIntegrityViolationException`, SQLSTATE 23503) | 9300/9800 −532 | 409 | CHILD_RECORDS_EXIST | 'Please delete associated child records first:' | −532 |
| Duplicate key on insert (`DataIntegrityViolationException`, SQLSTATE 23505) | 9700 −803 | 409 | DUPLICATE_KEY | 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:' | −803 |
| Lock/deadlock (`PessimisticLockingFailureException`) | 9600/9200 −911 | 409 | LOCK_ERROR | 'Could not lock record for update' | −911 |
| Other `DataAccessException` / unexpected | WHEN OTHER | 500 | INTERNAL_ERROR | 'Error accessing: TRANSACTION_TYPE table. SQLCODE:' | −1 |
| Not signed on / not admin | sign-on | 401 / 403 | UNAUTHORIZED / FORBIDDEN | see §2 | – |

The UI composes the on-screen text exactly like the source STRING statements (see §4.4).

## 4. UI specification (React 18 + TypeScript + MUI + React Router)

Routes: `/login` (sign-on), `/admin` (AdminMenuPage, CA00 placeholder), `/transaction-types` (TransactionTypeListPage, CTLI), `/transaction-types/maintain` (TransactionTypeEditPage, CTTU; router state `from: 'list' | 'menu'`). Unauthenticated → `/login`; non-admin → access-denied message.

Common header (BMS rows 1–2): `Tran:` + tranid, title lines, `Date:` mm/dd/yy, `Prog:` + program name, `Time:` hh:mm:ss. `MessageBanner` renders `INFOMSG` (centred, neutral) and `ERRMSG` (red, bright) lines. Function keys are rendered as buttons labelled exactly like the BMS (`F2=Add`, ...) and are also bound to the physical F-keys.

### 4.1 TransactionTypeListPage (CTLI, map CTRTLIA)

| BMS field | React element / prop | Validation / behaviour |
|---|---|---|
| TRNNAME / PGMNAME | `<ScreenHeader tranId="CTLI" programName="COTRTLIC">` | |
| PAGENO | `pageNumber` → `Page {n}` | |
| TRTYPE | `<TextField name="typeFilter" inputProps={{maxLength:2}} autoFocus>` | disabled while an action is pending; red when invalid |
| TRDESC | `<TextField name="descFilter" inputProps={{maxLength:50}}>` | disabled while an action is pending |
| TRTSELn | `<TextField name="select-{n}" inputProps={{maxLength:1}}>` per row | only for populated rows; values `U`/`D` (upper-cased); red on invalid |
| TRTTYPn | table cell `row.trType` | read-only |
| TRTYPDn | `<TextField name="desc-{n}" inputProps={{maxLength:50}} readOnly>` | editable only on the row with pending `U` (`*` shown in red when blanked) |
| INFOMSG / ERRMSG | `<MessageBanner info error>` | |
| BUTNF02/03/07/08/10 | buttons `F2=Add`, `F3=Exit`, `F7=Page Up`, `F8=Page Dn`, `F10=Save` + `ENTER` | |

Behaviour (state = `{page, firstKey, nextCursor, hasNext, hasPrev, appliedFilters, pending}`):
- ENTER: if filters differ from applied → validate type filter, reload page 1 (REQ-CTLI-012). Otherwise validate actions: invalid code → 'Action code selected is invalid'; >1 → 'Please select only 1 action'; one `D` → pending delete ('Delete HIGHLIGHTED row ? Press F10 to confirm'); one `U` → pending update ('Update HIGHLIGHTED row. Press F10 to save'), and with a pending update ENTER validates the description; no action → refresh current page (`cursor=firstKey`).
- F7: page 1 → 'No previous pages to display'; else `direction=backward&cursor=firstKey&page=n-1`.
- F8: `hasNext` → `cursor=nextCursor&page=n+1`; else 'No more pages to display'.
- F10: pending delete → `DELETE`; success 'HIGHLIGHTED row deleted.Hit Enter to continue' and reload page 1. Pending update → client edits (1211) then `PUT ?upsert=false`; success 'HIGHLIGHTED row was updated' and refresh. F10 without pending action → ENTER.
- F2 → `/transaction-types/maintain` (state from=list). F3 → `/admin`.
- Default info: 'Type U to update, D to delete any record'.

### 4.2 TransactionTypeEditPage (CTTU, map CTRTUPA)

| BMS field | React element / prop | Validation |
|---|---|---|
| TRNNAME / PGMNAME | `<ScreenHeader tranId="CTTU" programName="COTRTUPC">` | |
| TRTYPCD | `<TextField name="trType" inputProps={{maxLength:2}} autoFocus>` | editable in states `NOT_FETCHED`, `DETAILS_NOT_FOUND`; 1245 messages |
| TRTYDSC | `<TextField name="description" inputProps={{maxLength:50}}>` | editable in `SHOW_DETAILS`, `CREATE_NEW_RECORD`, `CHANGES_NOT_OK`; 1230 messages |
| INFOMSG / ERRMSG | `<MessageBanner>` | |
| FKEYS | `ENTER` + `F3=Exit` buttons | ENTER hidden in `CONFIRM_DELETE` |
| FKEY04 | `F4=Delete` | visible in `SHOW_DETAILS`, `CONFIRM_DELETE` |
| FKEY05 | `F5=Save` | visible in `CHANGES_OK_NOT_CONFIRMED`, `DETAILS_NOT_FOUND` |
| FKEY06 | not rendered | never shown in source |
| FKEY12 | `F12=Cancel` | visible in `CHANGES_OK_NOT_CONFIRMED`, `SHOW_DETAILS`, `DETAILS_NOT_FOUND`, `CONFIRM_DELETE`, `CREATE_NEW_RECORD` |

State machine (`TTUP-CHANGE-ACTION` → TypeScript union): `NOT_FETCHED` → (ENTER, `GET` 200) `SHOW_DETAILS` | (404) `DETAILS_NOT_FOUND` → (F5) `CREATE_NEW_RECORD`; `SHOW_DETAILS`/`CREATE_NEW_RECORD` → (ENTER valid change) `CHANGES_OK_NOT_CONFIRMED` | (invalid) `CHANGES_NOT_OK`; `CHANGES_OK_NOT_CONFIRMED` → (F5, `PUT ?upsert=true`) `CHANGES_OKAYED_AND_DONE` | `CHANGES_OKAYED_LOCK_ERROR` | `CHANGES_OKAYED_BUT_FAILED`; `SHOW_DETAILS` → (F4) `CONFIRM_DELETE` → (F4, `DELETE`) `DELETE_DONE` | `DELETE_FAILED` | (−532) `START_DELETE`; F12 per REQ-CTTU-014 (validated → `CHANGES_BACKED_OUT` 'Update was cancelled'; delete-confirm → `DETAILS_NOT_FETCHED` 'Delete was cancelled'; show/not-found/create → blank search screen); terminal states (`*_DONE`, `*_FAILED`) reset to `NOT_FETCHED` on the next key (0000-MAIN reset). Info line per discovery §6.2. Keys not valid in a state → 'Invalid key pressed'.

### 4.3 Client validation = BMS attributes

| BMS attribute | HTML / React equivalent |
|---|---|
| `LENGTH=n` | `inputProps.maxLength = n` |
| `ATTRB=PROT/ASKIP` | `InputProps.readOnly` / `disabled` |
| `ATTRB=UNPROT` | editable `TextField` |
| `IC` / `CURSOR` | `autoFocus` / `inputRef.focus()` |
| `COLOR=RED` set by program on error | `error` prop |
| `DRK` PF-key labels | button not rendered |
| program edits (1210/1220/1230/1240/1245) | same regex/literal in `src/validation.ts` (shared with API literals) |

### 4.4 On-screen message composition (source STRING formats)

| Screen | Error code | Text |
|---|---|---|
| CTLI | CHILD_RECORDS_EXIST | `Please delete associated child records first: SQLCODE: -532` (9999-FORMAT-DB2-MESSAGE) |
| CTLI | NOT_FOUND (PUT) | `Record not found. Deleted by others ? SQLCODE:  100` |
| CTLI | LOCK_ERROR | `Deadlock. Someone else updating ? SQLCODE: -911` |
| CTLI | other on delete / update | `Delete failed with message: SQLCODE: <n>` / `Update failed with SQLCODE: <n>` |
| CTTU | CHILD_RECORDS_EXIST | `Please delete associated child records first:SQLCODE : -532:` |
| CTTU | DUPLICATE_KEY | `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803:` |
| CTTU | LOCK_ERROR | `Could not lock record for update` |
| CTTU | NOT_FOUND (GET) | `No record found for this key in database` |

`<n>` uses COBOL `PIC ----9` editing (right-aligned in 5 positions). DB2 `SQLERRM` / DSNTIAC text has no PostgreSQL equivalent and is omitted (D-08).

## 5. Build / run

- API: `cd carddemo-trantype-api && mvn verify` (Testcontainers needs Docker). Run: `docker compose up -d` (repo root `docker-compose.yml`, PostgreSQL 15) then `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.
- UI: `cd carddemo-trantype-ui && npm ci && npm test && npm run build`; dev: `npm run dev` (Vite on 5173, proxy `/api` → 8080).
