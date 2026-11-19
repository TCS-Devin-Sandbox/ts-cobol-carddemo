# Phase 4 — Target specification

Target: `carddemo-trantype-api` (Spring Boot 3.3, Java 17, Spring Data JPA, Bean Validation, Flyway, PostgreSQL 15) and `carddemo-trantype-ui` (React 18, TypeScript, Vite, MUI 5, React Router 6, axios).
Requirements: `requirements-ctli-cttu.md`. Deviations from source: `deviations.md`.

## 1. Architecture

```
Browser (React SPA)  ──JWT bearer──▶  /api/**  Spring Boot API  ──JPA/JDBC──▶  PostgreSQL 15 (Flyway V1, V2)
  LoginPage (COSGN00C stand-in)        AuthController   (POST /api/auth/login)
  AdminMenuPage (COADM01C stand-in)    TransactionTypeController
  TransactionTypeListPage  (CTLI)      TransactionTypeService (keyset paging, upsert, validation)
  TransactionTypeEditPage  (CTTU)      GlobalExceptionHandler (@ControllerAdvice)
```

The 3270 pseudo-conversation (COMMAREA carried between `EXEC CICS RETURN TRANSID`) is replaced by:
- **Stateless API**: each request carries all context (cursor, direction, filters). No server session.
- **Client state**: the per-screen state (CTLI selections/pending action, CTTU `TTUP-CHANGE-ACTION`) lives in React component state.

## 2. Authentication and authorization

**Choice: JWT (stateless bearer tokens)** rather than server sessions. Reasons: the API is stateless like the rest of the design (no COMMAREA, no session), there is no CSRF exposure for a token kept in memory, and it scales horizontally.

- `POST /api/auth/login` checks credentials against the configured user store and returns an HS256-signed JWT (`sub` = user id, `roles` = [`ADMIN`] or [`USER`], `exp` = 1 h by default). Stand-in for the CardDemo sign-on (COSGN00C, user type `A`/`U` in USRSEC).
- The user store is configured in `application.yml` (`carddemo.security.users`, BCrypt-hashed passwords). Default demo users: `ADMIN001` / `PASSWORD` (type `A` → `ROLE_ADMIN`) and `USER0001` / `PASSWORD` (type `U` → `ROLE_USER`). These mirror the CardDemo sample users.
- The resource server is Spring Security `oauth2ResourceServer().jwt()` with `NimbusJwtDecoder` (HMAC secret `carddemo.security.jwt-secret`, ≥ 32 bytes, from the `CARDDEMO_JWT_SECRET` env var in non-dev).
- Rules: `/api/auth/**` permitAll; `/api/transaction-types/**` `hasRole('ADMIN')`; `/actuator/health` permitAll; everything else authenticated.
- 401 → `{"code":"UNAUTHORIZED","message":"Authentication required"}`; 403 → `{"code":"FORBIDDEN","message":"No access - Admin Only option... "}` (literal from `COMEN01C`, shown when a regular user selects an admin option).
- Bad credentials on login → 401 `{"code":"UNAUTHORIZED","message":"Wrong Password. Try again ..."}` (COSGN00C literal). Blank user id → 400 `Please enter User ID ...`; blank password → 400 `Please enter Password ...`.

## 3. REST contract (OpenAPI 3.0)

```yaml
openapi: 3.0.3
info:
  title: CardDemo Transaction Type API
  version: 1.0.0
  description: Modernization of CICS transactions CTLI (COTRTLIC) and CTTU (COTRTUPC).
servers:
  - url: http://localhost:8080
security:
  - bearerAuth: []
paths:
  /api/auth/login:
    post:
      summary: Sign on (COSGN00C stand-in)
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/LoginRequest' }
      responses:
        '200': { description: Token issued, content: { application/json: { schema: { $ref: '#/components/schemas/LoginResponse' } } } }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
  /api/transaction-types:
    get:
      summary: List one keyset page (CTLI 8000-READ-FORWARD / 8100-READ-BACKWARDS)
      parameters:
        - name: cursor
          in: query
          description: >
            Keyset cursor. direction=next → rows with trType >= cursor (use nextCursor from the previous page;
            omit for the first page). direction=prev → rows with trType < cursor (use firstKey of the current page).
          schema: { type: string, pattern: '^[0-9]{2}$' }
        - name: direction
          in: query
          schema: { type: string, enum: [next, prev], default: next }
        - name: size
          in: query
          schema: { type: integer, minimum: 1, maximum: 50, default: 7 }
        - name: typeFilter
          in: query
          description: Exact 2-digit type code; blank or 00 = no filter (1220-EDIT-TYPECD).
          schema: { type: string }
        - name: descFilter
          in: query
          description: Substring match, LIKE '%value%' (1230-EDIT-DESC).
          schema: { type: string, maxLength: 50 }
      responses:
        '200': { description: Page, content: { application/json: { schema: { $ref: '#/components/schemas/TransactionTypePage' } } } }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '500': { $ref: '#/components/responses/ServerError' }
    post:
      summary: Insert a new transaction type (CTTU add; 9700-INSERT-RECORD)
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/TransactionTypeRequest' }
      responses:
        '201':
          description: Created
          headers: { Location: { schema: { type: string } } }
          content: { application/json: { schema: { $ref: '#/components/schemas/TransactionType' } } }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '409': { $ref: '#/components/responses/Conflict' }
        '500': { $ref: '#/components/responses/ServerError' }
  /api/transaction-types/{trType}:
    parameters:
      - name: trType
        in: path
        required: true
        description: 1–2 digit numeric code, non-zero; normalized to 2 digits (1 → 01).
        schema: { type: string }
    get:
      summary: Read one (CTTU 9100-GET-TRANSACTION-TYPE)
      responses:
        '200': { description: Found, content: { application/json: { schema: { $ref: '#/components/schemas/TransactionType' } } } }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '500': { $ref: '#/components/responses/ServerError' }
    put:
      summary: Update description (CTLI 9200; with upsert=true CTTU 9600 UPDATE then INSERT on +100)
      parameters:
        - name: upsert
          in: query
          schema: { type: boolean, default: false }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/TransactionTypeUpdateRequest' }
      responses:
        '200': { description: Updated, content: { application/json: { schema: { $ref: '#/components/schemas/TransactionType' } } } }
        '201': { description: Inserted (upsert=true and row absent), content: { application/json: { schema: { $ref: '#/components/schemas/TransactionType' } } } }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '500': { $ref: '#/components/responses/ServerError' }
    delete:
      summary: Delete (CTLI 9300 / CTTU 9800)
      responses:
        '204': { description: Deleted }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '500': { $ref: '#/components/responses/ServerError' }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
  schemas:
    LoginRequest:
      type: object
      required: [userId, password]
      properties:
        userId: { type: string, maxLength: 8 }
        password: { type: string, maxLength: 8 }
    LoginResponse:
      type: object
      properties:
        token: { type: string }
        userId: { type: string }
        userType: { type: string, enum: [A, U] }
        expiresAt: { type: string, format: date-time }
    TransactionType:
      type: object
      properties:
        trType: { type: string, pattern: '^[0-9]{2}$', example: '01' }
        trDescription: { type: string, maxLength: 50, example: PURCHASE }
    TransactionTypeRequest:
      type: object
      required: [trType, trDescription]
      properties:
        trType: { type: string, description: 'Tran Type code edits (1245-EDIT-NUM-REQD)' }
        trDescription: { type: string, description: 'Transaction Desc edits (1230/1240)' }
    TransactionTypeUpdateRequest:
      type: object
      required: [trDescription]
      properties:
        trDescription: { type: string }
    TransactionTypePage:
      type: object
      properties:
        rows: { type: array, maxItems: 50, items: { $ref: '#/components/schemas/TransactionType' } }
        firstKey: { type: string, nullable: true, description: 'WS-CA-FIRST-TR-CODE; cursor for direction=prev' }
        lastKey: { type: string, nullable: true, description: 'Key of the last displayed row' }
        nextCursor: { type: string, nullable: true, description: 'Look-ahead row key (WS-CA-LAST-TR-CODE when CA-NEXT-PAGE-EXISTS); cursor for direction=next' }
        hasNext: { type: boolean, description: 'CA-NEXT-PAGE-EXISTS' }
        hasPrev: { type: boolean, description: 'NOT CA-FIRST-PAGE' }
        pageNumber: { type: integer, description: 'WS-CA-SCREEN-NUM (derived: rows before firstKey / size + 1)' }
        size: { type: integer }
        infoMessage: { type: string, nullable: true, description: 'INFOMSG equivalent' }
        errorMessage: { type: string, nullable: true, description: 'ERRMSG equivalent for non-error conditions (empty result, no previous page)' }
    ErrorResponse:
      type: object
      required: [code, message]
      properties:
        code: { type: string, enum: [VALIDATION_ERROR, NO_CHANGE, NOT_FOUND, DUPLICATE_KEY, FK_RESTRICT, LOCK_CONFLICT, DB_ERROR, UNAUTHORIZED, FORBIDDEN] }
        message: { type: string }
  responses:
    BadRequest: { description: Validation error, content: { application/json: { schema: { $ref: '#/components/schemas/ErrorResponse' } } } }
    Unauthorized: { description: Not authenticated, content: { application/json: { schema: { $ref: '#/components/schemas/ErrorResponse' } } } }
    Forbidden: { description: Not an admin, content: { application/json: { schema: { $ref: '#/components/schemas/ErrorResponse' } } } }
    NotFound: { description: No row, content: { application/json: { schema: { $ref: '#/components/schemas/ErrorResponse' } } } }
    Conflict: { description: Duplicate / FK restrict / lock, content: { application/json: { schema: { $ref: '#/components/schemas/ErrorResponse' } } } }
    ServerError: { description: Other DB error, content: { application/json: { schema: { $ref: '#/components/schemas/ErrorResponse' } } } }
```

### 3.1 Keyset pagination algorithm

| Step | Forward (`direction=next`) | Backward (`direction=prev`) |
|---|---|---|
| Query | `WHERE tr_type >= :cursor AND <filters> ORDER BY tr_type LIMIT size+1` (cursor omitted → no lower bound) | `WHERE tr_type < :cursor AND <filters> ORDER BY tr_type DESC LIMIT size`, then reverse |
| hasNext | `size+1`-th row exists → `true`, `nextCursor` = its key | `true` (source sets CA-NEXT-PAGE-EXISTS); `nextCursor` = old `cursor` (source moves FIRST key to LAST key) |
| Empty prev (no row < cursor) | — | first page forward + `errorMessage = "No previous pages to display"` |
| Short prev (< size rows, concurrent delete) | — | first page forward (deviation D-07) |
| pageNumber | `COUNT(*) WHERE tr_type < firstKey AND <filters>` / size + 1 | same |
| hasPrev | that count > 0 | same |
| Filters | `typeFilter` → `tr_type = :t`; `descFilter` → `tr_description LIKE '%' ‖ trim(:d) ‖ '%'` | same |

Messages on 200 responses: rows empty and no filters → `errorMessage = "No records found for this search condition."`; filters supplied and zero matches → `errorMessage = "No Records found for these filter conditions"`; otherwise `infoMessage = "Type U to update, D to delete any record"`.

### 3.2 Validation (identical strings)

| Input | Rule (source) | Message | Status |
|---|---|---|---|
| path `trType`, body `trType` | blank (null, empty, whitespace, `*`) | `Tran Type code must be supplied.` | 400 |
| | not 1–2 digits | `Tran Type code must be numeric.` | 400 |
| | numeric value 0 | `Tran Type code must not be zero.` | 400 |
| | valid | zero-padded to 2 | — |
| `trDescription` | blank / `*` | `Transaction Desc must be supplied.` | 400 |
| | not `[A-Za-z0-9 ]` | `Transaction Desc can have numbers or alphabets only.` | 400 |
| | > 50 after trim | `Transaction Desc must not exceed 50 characters.` *(new)* | 400 |
| `typeFilter` | non-blank, not `00`, not `^[0-9]{2}$` | `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER` | 400 |
| `descFilter` | > 50 | `Description filter must not exceed 50 characters.` *(new)* | 400 |
| `size` | outside 1..50 | `Page size must be between 1 and 50.` *(new)* | 400 |
| `direction` | not next/prev | `Direction must be next or prev.` *(new)* | 400 |
| PUT description equal (case-insensitive, trimmed) to stored | 1211 / 1205 | upsert=false: `No change detected with respect to database values.`; upsert=true: `No change detected with respect to values fetched.` | 400 `NO_CHANGE` |

When both `trType` and `trDescription` fail, the `trType` message is returned (CTTU edits the key first).

### 3.3 Error mapping (source literal → HTTP)

DB2-derived messages keep the source literal and add ` SQLCODE: <db2-equivalent>` like `9999-FORMAT-DB2-MESSAGE` (`TRIM(action) ' SQLCODE:' code`). Spacing is normalized to one blank and the DSNTIAC/SQLERRM text is omitted.

| Situation | Source ¶ / SQLCODE | `code` | HTTP | `message` |
|---|---|---|---|---|
| GET one, absent | CTTU 9100 +100 | NOT_FOUND | 404 | `No record found for this key in database` |
| PUT (upsert=false), absent | CTLI 9200 +100 | NOT_FOUND | 404 | `Record not found. Deleted by others ? SQLCODE: 100` |
| DELETE, absent | CTLI 9300 OTHER (+100) | NOT_FOUND | 404 | `Delete failed with message: SQLCODE: 100` |
| DELETE parent with children | CTLI 9300 / CTTU 9800 -532 | FK_RESTRICT | 409 | `Please delete associated child records first: SQLCODE: -532` |
| POST existing key | CTTU 9700 -803 | DUPLICATE_KEY | 409 | `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803` |
| PUT lock/deadlock, upsert=false | CTLI 9200 -911 | LOCK_CONFLICT | 409 | `Deadlock. Someone else updating ? SQLCODE: -911` |
| PUT lock/deadlock, upsert=true | CTTU 9600 -911 | LOCK_CONFLICT | 409 | `Could not lock record for update` |
| Other DB error on PUT (upsert=false) | CTLI 9200 <0 | DB_ERROR | 500 | `Update failed with SQLCODE: -999` |
| Other DB error on PUT (upsert=true) | CTTU 9600 <0 | DB_ERROR | 500 | `Error updating: TRANSACTION_TYPE Table. SQLCODE: -999` |
| Other DB error on DELETE | 9300/9800 OTHER | DB_ERROR | 500 | `Delete failed with message: SQLCODE: -999` |
| Other DB error on GET one | CTTU 9100 <0 | DB_ERROR | 500 | `Error accessing: TRANSACTION_TYPE table. SQLCODE: -999` |
| Other DB error on list | CTLI 8000/9100 | DB_ERROR | 500 | `Error reading TRANSACTION_TYPE table SQLCODE: -999` |
| Other DB error on POST (non-duplicate) | CTTU 9700 OTHER | DB_ERROR | 500 | `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -999` |
| Validation | see 3.2 | VALIDATION_ERROR / NO_CHANGE | 400 | literal |
| No/invalid token | — | UNAUTHORIZED | 401 | `Authentication required` |
| Non-admin | — | FORBIDDEN | 403 | `No access - Admin Only option... ` |

`-999` is a placeholder for DB2 codes that have no PostgreSQL equivalent. The PostgreSQL SQLSTATE is logged server-side, not returned.

### 3.4 DTOs (Java)

```java
record TransactionTypeDto(String trType, String trDescription) {}
record TransactionTypeRequest(@TranTypeCode String trType, @TranTypeDescription String trDescription) {}
record TransactionTypeUpdateRequest(@TranTypeDescription String trDescription) {}
record TransactionTypePage(List<TransactionTypeDto> rows, String firstKey, String lastKey, String nextCursor,
                           boolean hasNext, boolean hasPrev, int pageNumber, int size,
                           String infoMessage, String errorMessage) {}
record ErrorResponse(String code, String message) {}
record LoginRequest(String userId, String password) {}
record LoginResponse(String token, String userId, String userType, Instant expiresAt) {}
```

## 4. UI specification

Routes (React Router):

| Route | Page | Source |
|---|---|---|
| `/login` | `LoginPage` | COSGN00C (stand-in) |
| `/admin` | `AdminMenuPage` — placeholder for COADM01C. Lists options 1–6 (1–4 disabled "not migrated"), 5 → `/transaction-types`, 6 → `/transaction-types/maintain`. Non-admins see `No access - Admin Only option... ` | COADM01C, COADM02Y |
| `/transaction-types` | `TransactionTypeListPage` | CTLI / COTRTLIC / CTRTLIA |
| `/transaction-types/maintain` | `TransactionTypeEditPage` | CTTU / COTRTUPC / CTRTUPA |

Shared components: `ScreenHeader` (Tran/Prog/Date/Time/titles), `MessageBanner` (info line = INFOMSG, error line = ERRMSG), `FunctionKeyBar` (buttons + keyboard F-keys), `RequireAdmin` route guard. Keyboard: `Enter`, `F2`, `F3`, `F4`, `F5`, `F7`, `F8`, `F10`, `F12` handled by `useFunctionKeys` (browser default prevented).

### 4.1 TransactionTypeListPage (CTLI)

- Filters: Type Filter (2), Description Filter (50).
- Table with exactly 7 slots: Select (1-char text input, accepts any char so invalid codes can be reported), Type, Description (read-only `TextField`, editable only for the pending `U` row).
- Buttons: `Enter=Process`, `F2=Add`, `F3=Exit`, `F7=Page Up`, `F8=Page Dn`, `F10=Save`. Page label `Page n`.
- Client logic (mirrors 1210/2300/2500):
  - **Process (Enter)**: if filters changed → load first page with filters (REQ-CTLI-013). Otherwise evaluate selections: invalid code → `Action code selected is invalid`; >1 → `Please select only 1 action`; one `D` → pending delete (row highlighted, info `Delete HIGHLIGHTED row ? Press F10 to confirm`); one `U` → pending update (description editable, info `Update HIGHLIGHTED row. Press F10 to save`; edits/no-change message shown as in 1211); none → reload current page.
  - **F10**: only with a pending action and unchanged filters/selection; otherwise = Enter. Delete → `DELETE`; on 204 reload page 1 and show `HIGHLIGHTED row deleted.Hit Enter to continue`. Update → validate (required / alnum / ≤50 / no-change) → `PUT ?upsert=false`; on 200 reload current page and show `HIGHLIGHTED row was updated`. API errors → error line with the API message.
  - **F7**: page 1 → `No previous pages to display`; else `GET direction=prev&cursor=firstKey`.
  - **F8**: `hasNext=false` → `No more pages to display`; else `GET direction=next&cursor=nextCursor`.
  - **F2** → `/transaction-types/maintain`; **F3** → `/admin`.

BMS field → React mapping (CTRTLIA):

| BMS field | Component / prop | Notes |
|---|---|---|
| TRNNAME | `ScreenHeader.tranId="CTLI"` | |
| PGMNAME | `ScreenHeader.programName="COTRTLIC"` | |
| TITLE01 / TITLE02 | `ScreenHeader.title1/title2` | `AWS Mainframe Modernization` / `CardDemo` (COTTL01Y) |
| CURDATE / CURTIME | `ScreenHeader` clock (`MM/dd/yy`, `HH:mm:ss`) | |
| PAGENO | `page.pageNumber` → `Typography data-testid="page-number"` | |
| TRTYPE | `TextField name="typeFilter" inputProps.maxLength=2` | `error` when filter invalid (red) |
| TRDESC | `TextField name="descFilter" inputProps.maxLength=50` | |
| TRTSEL1..7 | `TextField name="select-{i}" inputProps.maxLength=1` | `disabled` for empty slots / filter errors (DFHBMPRO); `error` red for bad selection |
| TRTTYP1..7 | `TableCell` text `row.trType` | read-only |
| TRTYPD1..7 | `TextField name="desc-{i}"` | `InputProps.readOnly` unless pending `U` row (DFHBMFSE); `error` when invalid |
| (row highlight DFHNEUTR) | `TableRow selected` | pending D/U row |
| TRTSELA/TRTTYPA/TRTDSCA | — | not rendered (unused 8th row) |
| INFOMSG | `MessageBanner.info` | centered |
| ERRMSG | `MessageBanner.error` | red |
| BUTNF02/03/07/08/10 | `FunctionKeyBar` buttons `F2=Add`, `F3=Exit`, `F7=Page Up`, `F8=Page Dn`, `F10=Save` | + keyboard |

### 4.2 TransactionTypeEditPage (CTTU)

State (`TTUP-CHANGE-ACTION` → `EditState`):

| Source value | `EditState` | Key editable | Desc editable | Buttons enabled | Info message |
|---|---|---|---|---|---|
| LOW-VALUES / `K` | `SEARCH` | yes | no | Enter, F3 | `Enter transaction type to be maintained` |
| `X` | `NOT_FOUND` | yes | no | Enter, F3, F5, F12 | `Press F05 to add. F12 to cancel` |
| `S` | `SHOW` | no | yes | Enter, F3, F4, F12 | `Enter transaction type to be maintained` (source 3250) |
| `R` | `CREATE` | no | yes | Enter, F3, F12 | `Enter new transaction type details.` |
| `E` | `CHANGES_NOT_OK` | no | yes | Enter, F3 | `Update transaction type details shown.` |
| `B` | `BACKED_OUT` | no | yes | Enter, F3 | `Update transaction type details shown.` |
| `N` | `CONFIRM_SAVE` | no | no | Enter, F3, F5, F12 | `Changes validated.Press F5 to save` |
| `9` | `CONFIRM_DELETE` | no | no | F3, F4, F12 | `Delete this record ? Press F4 to confirm` |
| `C` | `SAVED` | no | no | Enter, F3 | `Changes committed to database` |
| `7` | `DELETED` | no | no | Enter, F3 | `Delete successful.` |
| `6`/`L`/`F` | `FAILED` | no | no | Enter, F3 | `Changes unsuccessful` |

Transitions: `SEARCH --Enter--> GET /{key}` → `SHOW` | `NOT_FOUND` (error `No record found for this key in database`) | `SEARCH` with key edit error. `NOT_FOUND --F5--> CREATE`. `SHOW|CREATE|CHANGES_NOT_OK|BACKED_OUT --Enter-->` validate desc → `CONFIRM_SAVE` or `CHANGES_NOT_OK` (or stay with `No change detected with respect to values fetched.`). `CONFIRM_SAVE --F5-->` `POST` (from `CREATE`) or `PUT ?upsert=true` (edit) → `SAVED` / `FAILED` + API message. `SHOW --F4--> CONFIRM_DELETE --F4--> DELETE` → `DELETED` / `FAILED` (FK message). `CONFIRM_DELETE --F12-->` `SEARCH` + `Delete was cancelled`. `CONFIRM_SAVE --F12-->` `BACKED_OUT` + `Update was cancelled` (original values). `SHOW|CREATE|NOT_FOUND --F12--> SEARCH`. `SAVED|DELETED|FAILED --any key--> SEARCH` (fresh). Disallowed key → `Invalid key pressed`. `F3` → `/admin`.

BMS field → React mapping (CTRTUPA):

| BMS field | Component / prop | Notes |
|---|---|---|
| TRNNAME / PGMNAME | `ScreenHeader tranId="CTTU" programName="COTRTUPC"` | |
| TITLE01/02, CURDATE, CURTIME | `ScreenHeader` | |
| TRTYPCD | `TextField name="trType" inputProps.maxLength=2 disabled={!keyEditable}` | autoFocus (IC); `error` + `*` placeholder when blank |
| TRTYDSC | `TextField name="trDescription" inputProps.maxLength=50 disabled={!descEditable}` | `error` when invalid |
| INFOMSG | `MessageBanner.info` | |
| ERRMSG | `MessageBanner.error` | |
| FKEYS | buttons `Enter=Process`, `F3=Exit` | Enter disabled in `CONFIRM_DELETE` |
| FKEY04 | button `F4=Delete` | enabled in `SHOW`, `CONFIRM_DELETE` (DFHBMASB vs DFHBMDAR) |
| FKEY05 | button `F5=Save` | enabled in `CONFIRM_SAVE`, `NOT_FOUND` |
| FKEY06 | — | never active in source; not rendered |
| FKEY12 | button `F12=Cancel` | enabled in `CONFIRM_SAVE`, `SHOW`, `NOT_FOUND`, `CONFIRM_DELETE`, `CREATE` |

### 4.3 axios client

`src/api/client.ts`: base URL `import.meta.env.VITE_API_BASE_URL ?? ''` (Vite dev server proxies `/api` → `http://localhost:8080`), bearer token interceptor, error normalizer returning `{code, message}`. `src/api/transactionTypes.ts`: `listTransactionTypes`, `getTransactionType`, `createTransactionType`, `updateTransactionType(trType, desc, upsert)`, `deleteTransactionType`, `login`.
