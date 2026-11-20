# Phase 4 — Target specification

Target: `carddemo-trantype-api` (Spring Boot 3.3, Java 17, Spring Data JPA, Bean Validation, Flyway, PostgreSQL 15) and `carddemo-trantype-ui` (React 18, TypeScript, Vite, MUI 5, React Router 7, fetch).
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
- **Stateless API**: each request carries all context (cursor, direction, page, filters). No server session.
- **Client state**: the per-screen state (CTLI selections/pending action, CTTU `TTUP-CHANGE-ACTION`) lives in React component state.

## 2. Authentication and authorization

**Choice: JWT (stateless bearer tokens)**. The API has no server session; the UI stores `{token, username, roles}` in `sessionStorage` under `carddemo.session`. This is not an HttpOnly cookie or an in-memory-only token store.

- `POST /api/auth/login` accepts `{username, password}` and returns `{token, username, roles}`. The HS256 JWT has issuer `carddemo-trantype-api`, subject = username, a `roles` claim, and a default 60-minute lifetime (`carddemo.security.token-ttl-minutes`).
- `application.yml` configures users `admin` (`ADMIN`) and `user` (`USER`). `SecurityConfig` encodes configured passwords at startup. With `SPRING_PROFILES_ACTIVE=dev`, passwords default to `admin123` / `user123`; never enable this profile in shared environments.
- Outside development supply `JWT_SECRET` (at least 32 UTF-8 bytes), `ADMIN_PASSWORD`, and `USER_PASSWORD`. Database settings are `DB_URL`, `DB_USER`, and `DB_PASSWORD` (local defaults in `application.yml`). `carddemo.security.jwt-secret` resolves `JWT_SECRET`.
- Only `POST /api/auth/login` and `/actuator/health` (including subpaths) are public. Transaction-type endpoints require `ADMIN`; all other requests require authentication. The health endpoint returns `{"status":"UP"}` without a token when healthy.
- No/invalid token → 401 `UNAUTHORIZED`, `Authentication required`; non-admin → 403 `FORBIDDEN`, `Access restricted to administrators`.
- Invalid/blank login credentials → 401 `UNAUTHORIZED`, `Invalid user ID or password`; there are no separate blank-user/blank-password field messages. Malformed JSON uses the validation error handler.
- The UI rejects a non-admin login with `Access restricted to administrators` and does not store that session. `RequireAdmin` redirects missing or non-admin sessions to `/login`.

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
  /actuator/health:
    get:
      summary: Public Actuator health
      security: []
      responses:
        '200':
          description: Healthy
          content:
            application/json:
              schema:
                type: object
                properties:
                  status: { type: string, example: UP }
        '503': { description: Unhealthy }
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
            Keyset cursor. direction=FORWARD → rows with trType >= cursor (use nextCursor from the previous page;
            omit for the first page). direction=BACKWARD → rows with trType < cursor (use firstKey of the current page).
          schema: { type: string }
        - name: direction
          in: query
          schema: { type: string, enum: [FORWARD, BACKWARD], default: FORWARD }
        - name: size
          in: query
          schema: { type: integer, minimum: 1, maximum: 50, default: 7 }
        - name: page
          in: query
          description: Client-maintained page label (not an offset).
          schema: { type: integer, minimum: 1, default: 1 }
        - name: typeFilter
          in: query
          description: Trimmed 1–2 digits with optional leading plus; zero or blank = no filter; nonzero normalized to two digits.
          schema: { type: string }
        - name: descFilter
          in: query
          description: Substring match, LIKE '%value%' (1230-EDIT-DESC).
          schema: { type: string }
      responses:
        '200': { description: Page, content: { application/json: { schema: { $ref: '#/components/schemas/TransactionTypePage' } } } }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '500': { $ref: '#/components/responses/ServerError' }
    post:
      summary: Explicit insert-only operation (9700-INSERT-RECORD concept; CTTU UI uses PUT)
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/TransactionTypeCreateRequest' }
      responses:
        '201':
          description: Created
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
        description: Trimmed 1–2 digits with optional leading plus, nonzero; normalized to 2 digits (1 → 01).
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
      summary: Save description, inserting when absent (CTTU)
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/TransactionTypeUpdateRequest' }
      responses:
        '200': { description: Updated, content: { application/json: { schema: { $ref: '#/components/schemas/TransactionType' } } } }
        '201': { description: Inserted (row absent), content: { application/json: { schema: { $ref: '#/components/schemas/TransactionType' } } } }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '409': { $ref: '#/components/responses/Conflict' }
        '500': { $ref: '#/components/responses/ServerError' }
    patch:
      summary: Update an existing description only (CTLI)
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/TransactionTypeUpdateRequest' }
      responses:
        '200': { description: Updated, content: { application/json: { schema: { $ref: '#/components/schemas/TransactionType' } } } }
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
      required: [username, password]
      properties:
        username: { type: string }
        password: { type: string }
    LoginResponse:
      type: object
      properties:
        token: { type: string }
        username: { type: string }
        roles: { type: array, items: { type: string } }
    TransactionType:
      type: object
      properties:
        trType: { type: string, pattern: '^[0-9]{2}$', example: '01' }
        trDescription: { type: string, maxLength: 50, example: PURCHASE }
    TransactionTypeCreateRequest:
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
        firstKey: { type: string, nullable: true, description: 'WS-CA-FIRST-TR-CODE; cursor for direction=BACKWARD' }
        lastKey: { type: string, nullable: true, description: 'Key of the last displayed row' }
        nextCursor: { type: string, nullable: true, description: 'Look-ahead row key (WS-CA-LAST-TR-CODE when CA-NEXT-PAGE-EXISTS); cursor for direction=FORWARD' }
        hasNext: { type: boolean, description: 'CA-NEXT-PAGE-EXISTS' }
        hasPrev: { type: boolean, description: 'NOT CA-FIRST-PAGE' }
        pageNumber: { type: integer, description: 'WS-CA-SCREEN-NUM (client page if hasPrev (minimum 2), otherwise 1)' }
        message: { type: string, nullable: true, description: 'Empty-result message; null on a populated page' }
    ErrorResponse:
      type: object
      required: [code, message]
      properties:
        code: { type: string, enum: [VALIDATION_ERROR, NO_CHANGE, NOT_FOUND, DUPLICATE_KEY, FK_RESTRICT, LOCK_ERROR, DB_ERROR, UNAUTHORIZED, FORBIDDEN] }
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

| Step | Forward (`direction=FORWARD`) | Backward (`direction=BACKWARD`) |
|---|---|---|
| Query | `tr_type >= cursor`, ascending, `size+1`; omit cursor for lowest key | `tr_type < cursor`, descending, `size`; reverse for display |
| nextCursor | Extra look-ahead row key, or null | First matching key after the last displayed row; if no earlier rows, first matching key at/after cursor |
| hasNext | `nextCursor != null` | `nextCursor != null` |
| hasPrev | Matching row exists before first displayed key | Same |
| pageNumber | No earlier row → 1; otherwise `max(page, 2)` | Same; UI passes previous page number |
| Filters | Normalized type equality and case-sensitive `LIKE '%' + trim(descFilter) + '%'`; `%`/`_` remain wildcards | Same |

`page` defaults to 1 and must be positive; it labels the page and does not affect keyset selection. The UI sends `pageNumber+1` or `pageNumber-1` when navigating.

All empty responses have `rows=[]`, null keys/cursor, both flags false, pageNumber 1. With no cursor and an active filter matching zero rows: `message = "No Records found for these filter conditions"`. Other empty first requests use `No records found for this search condition.`; empty requests with a cursor use `No more pages for these search conditions`. There is no short-backward-page fallback. A populated page has `message=null`; UI info text is client-side.

### 3.2 Validation (current API and UI)

| Input | Rule | Message / behavior | Status |
|---|---|---|---|
| path/body `trType` | null, empty or whitespace | `Tran Type code must be supplied.` | 400 |
| | not trimmed `\+?\d{1,2}` (including literal `*`) | `Tran Type code must be numeric.` | 400 |
| | numeric zero | `Tran Type code must not be zero.` | 400 |
| | valid | Strip plus and zero-pad to 2 digits | — |
| `trDescription` | blank | `Transaction Desc must be supplied.` | 400 |
| | not ASCII letters/digits/spaces (including literal `*`) | `Transaction Desc can have numbers or alphabets only.` | 400 |
| | >50 after trim | `Transaction Desc must not exceed 50 characters.` | 400 |
| `typeFilter` | blank or numeric zero | No filter | — |
| | otherwise | Same optional-plus 1–2-digit syntax as codes; invalid → `TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER` | 400 |
| `descFilter` | blank / nonblank | No filter / trimmed substring pattern; no API maximum-length edit | — |
| `size`, `page`, `direction` | size outside 1..50; page <1; direction not FORWARD/BACKWARD | `Invalid request parameter` | 400 |
| PATCH unchanged description | Trimmed, case-insensitive comparison | `No change detected with respect to database values.` | 400 NO_CHANGE |
| PUT unchanged description | Same | `No change detected with respect to values fetched.` | 400 NO_CHANGE |

When both create-body fields fail, the key error wins. Rules live in `validation/TransactionTypeEdits`, reached through the `TranTypeRules` façade and `web.validation` Bean Validation constraints. The UI mirrors field edits in `src/validation.ts`; CTTU intercepts a blank search key first with `No input received`.

### 3.3 Error mapping (source literal → HTTP)

| Situation | code | HTTP | message |
|---|---|---|---|
| GET one absent | NOT_FOUND | 404 | `No record found for this key in database` |
| PATCH absent | NOT_FOUND | 404 | `Record not found. Deleted by others ?` |
| DELETE absent | NOT_FOUND | 404 | `Delete failed with message:SQLCODE :+100` |
| DELETE parent with children | FK_RESTRICT | 409 | `Please delete associated child records first:SQLCODE :-532` |
| POST duplicate | DUPLICATE_KEY | 409 | `Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:-803` |
| PATCH lock/deadlock | LOCK_ERROR | 409 | `Deadlock. Someone else updating ?` |
| PUT lock/deadlock | LOCK_ERROR | 409 | `Could not lock record for update` |
| Untranslated pessimistic lock failure | LOCK_ERROR | 409 | `Deadlock. Someone else updating ?` |
| Other DataAccessException (including other integrity errors) | DB_ERROR | 500 | `Error accessing: TRANSACTION_TYPE table. SQLCODE: SQLSTATE <state>` (`null` when no SQLSTATE is found) |
| Field validation / no change | VALIDATION_ERROR / NO_CHANGE | 400 | See 3.2 |
| Malformed JSON, constraint/type mismatch | VALIDATION_ERROR | 400 | `Invalid request parameter` |
| No/invalid token | UNAUTHORIZED | 401 | `Authentication required` |
| Non-admin | FORBIDDEN | 403 | `Access restricted to administrators` |

`GlobalExceptionHandler` preserves these exact strings and spacing, not a normalized SQLCODE format. PostgreSQL SQLSTATE is returned for generic data-access failures and the exception is logged. Each service write is transactional. Hikari initializes connections with `SET lock_timeout = '${carddemo.db.lock-timeout:5s}'`; integration tests override it to 1s.

### 3.4 DTOs (Java)

```java
record TransactionTypeDto(String trType, String trDescription) {}
record TransactionTypeCreateRequest(@TranTypeCode String trType, @TranTypeDescription String trDescription) {}
record TransactionTypeUpdateRequest(@TranTypeDescription String trDescription) {}
record TransactionTypePage(List<TransactionTypeDto> rows, String firstKey, String lastKey, String nextCursor,
                           boolean hasNext, boolean hasPrev, int pageNumber, String message) {}
record ErrorResponse(String code, String message) {}
record LoginRequest(String username, String password) {}
record LoginResponse(String token, String username, List<String> roles) {}
```

## 4. UI specification

Routes (React Router 7):

| Route | Page | Source |
|---|---|---|
| `/login` | `LoginPage` | COSGN00C stand-in |
| `/admin` | `AdminMenuPage`: options 5 and 6 plus Sign off (no disabled options 1–4) | COADM01C |
| `/transaction-types` | `TransactionTypeListPage` | CTLI / COTRTLIC |
| `/transaction-types/maintain` | `TransactionTypeEditPage`; `?from=list` controls F3 return | CTTU / COTRTUPC |

`RequireAdmin` redirects missing/non-admin sessions to `/login`. Shared components are `ScreenHeader`, `MessageLine` (`infomsg`/`errmsg`), and `FunctionKeyBar`.

`useFunctionKeys` binds CTLI Enter/F2/F3/F7/F8/F10 and state-valid CTTU Enter/F3/F4/F5/F12 to the same actions as buttons. Bound keys prevent browser defaults; auto-repeat is suppressed. Unbound keys are ignored. Single Enter on buttons, links, textareas, selects, summary elements, contenteditable fields, and button/link roles preserves native behavior; repeated Enter there is prevented to avoid accidental confirmation.

### 4.1 TransactionTypeListPage (CTLI)

- Filters: Type Filter (2-character input), Description Filter (50-character input). Enter applies changed filters and clears paging/selections.
- Table renders only returned rows (up to 7), not padded empty slots. Selection is one character; descriptions become editable when their action text is `U`, before Enter. Enter stages one valid U/D action and highlights it.
- Buttons: `ENTER`, `F2=Add`, `F3=Exit`, `F7=Page Up`, `F8=Page Dn`, `F10=Save`. F10 button is disabled without a pending action; keyboard F10 without a pending action invokes Enter.
- F10 deletes with DELETE (then reloads page 1) or validates and updates with PATCH (then reloads current query). Blank/invalid/no-change descriptions stop the update. Changing an action clears pending state; changing only a filter does not clear pending state until Enter applies it.
- F7/F8 use BACKWARD/firstKey and FORWARD/nextCursor respectively, sending the adjacent page number. Boundaries report `No previous pages to display` / `No more pages to display` without a request.
- F2 opens `/transaction-types/maintain?from=list`; F3 returns to `/admin` with info `PF03 pressed. Exiting`.

BMS field → React mapping (CTRTLIA):

| BMS field | Component / prop | Notes |
|---|---|---|
| TRNNAME / PGMNAME | `ScreenHeader tranId="CTLI" program="COTRTLIC"` | |
| TITLE01/02 | Header banner plus `title="Maintain Transaction Type"` | |
| CURDATE / CURTIME | `toLocaleDateString()` / `toLocaleTimeString()` | Client time at render, not a live timer or fixed locale |
| PAGENO | `pageNumber`, `data-testid="pageno"` | |
| TRTYPE / TRDESC | `TextField` labels Type Filter / Description Filter | maxLength 2 / 50 |
| TRTSEL1..7 | `TextField` aria-label `action <key>` | Invalid action has error styling |
| TRTTYP1..7 | `TableCell` text | Read-only |
| TRTYPD1..7 | `TextField` aria-label `description <key>` | Disabled unless row action is U |
| Row highlight | `TableRow selected` | Pending U/D row |
| INFOMSG / ERRMSG | `MessageLine info/error` | Info centered, errors styled as error |
| BUTNF02/03/07/08/10 | `FunctionKeyBar` | Buttons plus keyboard |

### 4.2 TransactionTypeEditPage (CTTU)

State (`TTUP-CHANGE-ACTION` concepts → current `EditState`):

| Source concept | EditState | Key editable | Desc editable | Visible actions | Info |
|---|---|---|---|---|---|
| LOW-VALUES/K | NOT_FETCHED | yes | no | Enter, F3 | Enter transaction type to be maintained |
| X | DETAILS_NOT_FOUND | yes | no | Enter, F3, F5, F12 | Press F05 to add. F12 to cancel |
| S | SHOW_DETAILS | no | yes | Enter, F3, F4, F12 | Update transaction type details shown. |
| R | CREATE_NEW_RECORD | no | yes | Enter, F3, F12 | Enter new transaction type details. |
| E | CHANGES_NOT_OK | no | yes | Enter, F3 | Update transaction type details shown. |
| N | CHANGES_OK_NOT_CONFIRMED | no | no | Enter, F3, F5, F12 | Changes validated.Press F5 to save |
| 9 | CONFIRM_DELETE | no | no | F3, F4, F12 | Delete this record ? Press F4 to confirm |
| C | CHANGES_DONE | yes | no | Enter, F3 | Changes committed to database |
| 7 | DELETE_DONE | yes | no | Enter, F3 | Delete successful. |
| Save/delete failures | CHANGES_FAILED / DELETE_FAILED | yes | no | Enter, F3 | Changes unsuccessful |

Enter in key-entry states validates and fetches the key; blank input shows `No input received`. A found record goes to SHOW_DETAILS, absent to DETAILS_NOT_FOUND. Editing the key after not-found resets to NOT_FETCHED and requires a new lookup.

F5 from DETAILS_NOT_FOUND enters CREATE_NEW_RECORD. Enter in description-editing states validates and moves to CHANGES_OK_NOT_CONFIRMED (or reports no change/error). F5 there uses PUT for both add and edit; POST is available in the API/client but is not used by this page. Successful save sets CHANGES_DONE. Enter in CHANGES_OK_NOT_CONFIRMED has no page-processing action.

F4 from SHOW_DETAILS opens CONFIRM_DELETE; a second discrete F4 or button activation deletes. Enter processing is unbound in that state, and its button is hidden. F12 cancels delete to cleared key entry; it cancels save to SHOW_DETAILS with original values, or CREATE_NEW_RECORD when adding. Other offered F12 actions clear to key entry. Invalid/unbound keys are ignored, not reported as `Invalid key pressed`.

Completed/failed states already allow key entry. Enter performs a lookup of the current key rather than clearing it unconditionally. F3 returns to `/transaction-types` when `from=list`, otherwise `/admin`.

BMS field → React mapping (CTRTUPA):

| BMS field | Component / prop | Notes |
|---|---|---|
| TRNNAME / PGMNAME | `ScreenHeader tranId="CTTU" program="COTRTUPC"` | |
| TRTYPCD | `TextField` label Transaction Type | maxLength 2, numeric inputMode, autoFocus; no `*` placeholder |
| TRTYDSC | `TextField` label Description | maxLength 50, disabled outside description-editing states |
| INFOMSG / ERRMSG | `MessageLine info/error` | |
| FKEYS | `ENTER=Process`, `F3=Exit` | Enter hidden in CONFIRM_DELETE |
| FKEY04/05/12 | `F4=Delete`, `F5=Save`, `F12=Cancel` | Hidden unless valid in the current state |
| FKEY06 | Not rendered | |

### 4.3 fetch client

`src/api/client.ts` exports `api.login`, `list`, `get`, `create`, `save`, `updateDescription`, and `remove`. It uses relative `/api` URLs with browser `fetch`; the Vite `/api` proxy targets `API_URL` or `http://localhost:8080`. There is no axios client or VITE_API_BASE_URL setting.

`getSession`/`setSession` read/write `sessionStorage['carddemo.session']`. Requests add a bearer token except for login, add JSON Content-Type only with a body, handle 204 without parsing, and throw `ApiError(status, code, message)` on HTTP errors. Missing error fields fall back to `ERROR` / `HTTP <status>`; network errors remain native fetch errors. Sign off clears the session.
