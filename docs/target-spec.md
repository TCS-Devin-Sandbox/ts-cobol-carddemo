# Target specification — Transaction Type maintenance (Spring Boot + React)

Requirements: `docs/requirements-ctli-cttu.md`. Backend `carddemo-trantype-api` (port 8080), frontend `carddemo-trantype-ui` (port 5173, proxies `/api`).

## 1. Authentication (REQ-CTLI-027, REQ-CTTU-018, NFR-004)
Choice: **stateless JWT (HS256) bearer tokens**. The source has no per-transaction security (CICS relies on the admin menu); a stateless token matches the stateless cursor design and needs no server session or CSRF handling.
* `POST /api/auth/login` `{ "username", "password" }` → `200 { "token", "username", "roles": ["ADMIN"] }`; bad credentials → `401 {"code":"UNAUTHORIZED","message":"Invalid user ID or password"}`.
* Users are configured in `application.yml` (`carddemo.security.users`); defaults `admin/admin123` (ADMIN) and `user/user123` (USER) for local use only.
* Every `/api/transaction-types/**` call requires role ADMIN: missing/invalid token → 401 `UNAUTHORIZED`, non-admin → 403 `{"code":"FORBIDDEN","message":"Access restricted to administrators"}`.

## 2. REST API (OpenAPI 3)

```yaml
openapi: 3.0.3
info: { title: CardDemo Transaction Type API, version: 1.0.0 }
security: [ { bearerAuth: [] } ]
paths:
  /api/transaction-types:
    get:
      summary: Keyset page (CTLI 8000-READ-FORWARD / 8100-READ-BACKWARDS)
      parameters:
        - { name: cursor, in: query, schema: { type: string }, description: "start key; FORWARD = tr_type >= cursor, BACKWARD = tr_type < cursor. Omitted = first page" }
        - { name: direction, in: query, schema: { type: string, enum: [FORWARD, BACKWARD], default: FORWARD } }
        - { name: size, in: query, schema: { type: integer, default: 7, minimum: 1, maximum: 50 } }
        - { name: page, in: query, schema: { type: integer, default: 1, minimum: 1 }, description: "page number to display (WS-CA-SCREEN-NUM)" }
        - { name: typeFilter, in: query, schema: { type: string, maxLength: 2 } }
        - { name: descFilter, in: query, schema: { type: string, maxLength: 50 } }
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/TransactionTypePage" } } } }
        "400": { $ref: "#/components/responses/Error" }
    post:
      summary: Create (CTTU add; explicit insert)
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/TransactionTypeCreateRequest" } } } }
      responses:
        "201": { content: { application/json: { schema: { $ref: "#/components/schemas/TransactionType" } } } }
        "400": { $ref: "#/components/responses/Error" }
        "409": { $ref: "#/components/responses/Error" }
  /api/transaction-types/{trType}:
    parameters: [ { name: trType, in: path, required: true, schema: { type: string } } ]
    get:
      summary: Read one (CTTU 9100-GET-TRANSACTION-TYPE)
      responses: { "200": { content: { application/json: { schema: { $ref: "#/components/schemas/TransactionType" } } } }, "400": { $ref: "#/components/responses/Error" }, "404": { $ref: "#/components/responses/Error" } }
    put:
      summary: Save (CTTU 9600 update-then-insert upsert)
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/TransactionTypeUpdateRequest" } } } }
      responses: { "200": { description: updated }, "201": { description: inserted }, "400": { $ref: "#/components/responses/Error" }, "409": { $ref: "#/components/responses/Error" } }
    patch:
      summary: Inline description update (CTLI 9200-UPDATE-RECORD, strict)
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/TransactionTypeUpdateRequest" } } } }
      responses: { "200": { description: updated }, "400": { $ref: "#/components/responses/Error" }, "404": { $ref: "#/components/responses/Error" }, "409": { $ref: "#/components/responses/Error" } }
    delete:
      summary: Delete (CTLI 9300 / CTTU 9800)
      responses: { "204": { description: deleted }, "404": { $ref: "#/components/responses/Error" }, "409": { $ref: "#/components/responses/Error" } }
components:
  securitySchemes: { bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT } }
  responses:
    Error: { description: error, content: { application/json: { schema: { $ref: "#/components/schemas/ErrorResponse" } } } }
  schemas:
    TransactionType: { type: object, properties: { trType: { type: string, example: "01" }, trDescription: { type: string, example: PURCHASE } } }
    TransactionTypeCreateRequest: { type: object, properties: { trType: { type: string }, trDescription: { type: string } } }
    TransactionTypeUpdateRequest: { type: object, properties: { trDescription: { type: string } } }
    TransactionTypePage:
      type: object
      properties:
        rows: { type: array, items: { $ref: "#/components/schemas/TransactionType" } }
        firstKey: { type: string, nullable: true }
        lastKey: { type: string, nullable: true }
        nextCursor: { type: string, nullable: true, description: "lookahead key (WS-CA-LAST-TR-CODE); use with direction=FORWARD" }
        hasNext: { type: boolean }
        hasPrev: { type: boolean }
        pageNumber: { type: integer }
        message: { type: string, nullable: true, description: "source empty-result message" }
    ErrorResponse: { type: object, properties: { code: { type: string }, message: { type: string } } }
```

### Paging contract
* First page: no cursor → `tr_type >= ''` ascending, `LIMIT size+1`; the extra row sets `hasNext` and `nextCursor`.
* Next (F8): `cursor=nextCursor&direction=FORWARD&page=n+1`.
* Previous (F7): `cursor=firstKey&direction=BACKWARD&page=n-1` → `tr_type < cursor ORDER BY tr_type DESC LIMIT size`, reversed to ascending; `hasNext=true` when a row `> lastKey` exists.
* `hasPrev` = a row `< firstKey` exists with the same filters; when false `pageNumber` is forced to 1.
* Empty: unfiltered → `message: "No records found for this search condition."`; filtered → `"No Records found for these filter conditions"` (REQ-CTLI-008/011).

### Validation and source messages

| Rule | HTTP / code | Message (source literal) | Req |
|---|---|---|---|
| typeFilter not numeric | 400 VALIDATION_ERROR | TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER | CTLI-009 |
| trType blank | 400 VALIDATION_ERROR | Tran Type code must be supplied. | CTTU-003 |
| trType not numeric | 400 VALIDATION_ERROR | Tran Type code must be numeric. | CTTU-003 |
| trType zero | 400 VALIDATION_ERROR | Tran Type code must not be zero. | CTTU-003 |
| trDescription blank | 400 VALIDATION_ERROR | Transaction Desc must be supplied. | CTLI-016, CTTU-007 |
| trDescription invalid chars | 400 VALIDATION_ERROR | Transaction Desc can have numbers or alphabets only. | CTLI-016, CTTU-007 |
| trDescription > 50 (impossible on 3270, no source literal) | 400 VALIDATION_ERROR | Transaction Desc must not exceed 50 characters. | CTTU-007 |
| PATCH unchanged | 400 NO_CHANGE | No change detected with respect to database values. | CTLI-016 |
| PUT unchanged | 400 NO_CHANGE | No change detected with respect to values fetched. | CTTU-008 |
| GET missing | 404 NOT_FOUND | No record found for this key in database | CTTU-005 |
| PATCH missing (+100) | 404 NOT_FOUND | Record not found. Deleted by others ? | CTLI-018 |
| DELETE missing (+100) | 404 NOT_FOUND | Delete failed with message:SQLCODE :+100 | CTLI-022, CTTU-013 |
| POST duplicate (−803) | 409 DUPLICATE_KEY | Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:-803 | CTTU-011 |
| DELETE with children (−532) | 409 FK_RESTRICT | Please delete associated child records first:SQLCODE :-532 | CTLI-021, CTTU-013 |
| PATCH lock (−911) | 409 LOCK_ERROR | Deadlock. Someone else updating ? | CTLI-018 |
| PUT lock (−911) | 409 LOCK_ERROR | Could not lock record for update | CTTU-011 |
| other DB failure | 500 DB_ERROR | Update failed with / Error updating: TRANSACTION_TYPE Table. / Delete failed with message: … | CTLI-018/022, CTTU-011/013 |

Type codes in path/body are normalised like 1210-EDIT-TRANTYPE (`"1"` → `"01"`). Descriptions are trimmed.

## 3. UI specification

Routes: `/login`, `/admin` (admin-menu placeholder, options 5 and 6), `/transaction-types` (CTLI), `/transaction-types/maintain` (CTTU; `?from=list` when launched from the list).

### TransactionTypeListPage (CTRTLIA)
* Header (tran `CTLI`, program `COTRTLIC`, date/time, `Page n`), filter inputs, a 7-row table, info line, error line, action buttons.
* Row action select (blank / U / D); only the row with `U` has an editable description.
* ENTER button validates the selection (invalid code, more than one action) and shows the confirm prompt; F10 Save confirms (enabled only while a confirmation is pending).
* F7 Page Up / F8 Page Dn / F2 Add (→ maintain page `?from=list`) / F3 Exit (→ `/admin`, info 'PF03 pressed. Exiting').

| BMS field | React prop / element | HTML validation |
|---|---|---|
| TRTYPE (2, UNPROT, IC) | `typeFilter` TextField, autoFocus | `maxLength=2`, `inputMode=numeric` |
| TRDESC (50, UNPROT) | `descFilter` TextField | `maxLength=50` |
| TRTSELn (1) | `rows[n].action` select `'' \| 'U' \| 'D'` (free text accepted → server-equivalent check) | `maxLength=1` |
| TRTTYPn (2, PROT) | `rows[n].trType` read-only cell | — |
| TRTYPDn (50) | `rows[n].trDescription` TextField, `disabled` unless action U | `maxLength=50` |
| PAGENO | `pageNumber` | — |
| INFOMSG (45, centred) | `<MessageLine info>` | centred text |
| ERRMSG (78, BRT red) | `<MessageLine error>` | red, bold, `role=alert` |
| BUTNF02/F03/F07/F08/F10 | buttons `F2=Add`, `F3=Exit`, `F7=Page Up`, `F8=Page Dn`, `F10=Save` | — |

### TransactionTypeEditPage (CTRTUPA)
State machine mirrors `TTUP-CHANGE-ACTION`: `NOT_FETCHED → SHOW_DETAILS | DETAILS_NOT_FOUND → CREATE_NEW_RECORD → CHANGES_OK_NOT_CONFIRMED → DONE | FAILED`, `SHOW_DETAILS → CONFIRM_DELETE → DELETE_DONE | DELETE_FAILED`, F12 back-outs as in discovery §3. Info messages per state exactly as `3250-SETUP-INFOMSG`. Buttons shown per `3391-SETUP-PFKEY-ATTRS`: `ENTER=Process`, `F3=Exit`, `F4=Delete` (SHOW/CONFIRM-DELETE), `F5=Save` (CHANGES-OK/NOT-FOUND), `F12=Cancel` (most states).

| BMS field | React prop | HTML validation |
|---|---|---|
| TRTYPCD (2, UNPROT, IC; protected after fetch) | `trType` TextField, autoFocus, `disabled` when not in key-entry states | `maxLength=2`, `inputMode=numeric`, client check numeric / non-zero |
| TRTYDSC (50; unprotected in SHOW/NOT-OK/CREATE/BACKED-OUT) | `trDescription` TextField | `maxLength=50`, pattern `[A-Za-z0-9 ]*` |
| INFOMSG / ERRMSG | `<MessageLine>` | as list page |
| FKEY04/05/12 (DRK unless state) | conditional buttons | — |

### BMS attribute → HTML mapping

| BMS attribute | HTML / React |
|---|---|
| UNPROT | enabled input |
| PROT / ASKIP | read-only text or `disabled` input |
| IC | `autoFocus` |
| NUM / numeric edit | `inputMode="numeric"` + validation function |
| LENGTH=n | `maxLength={n}` |
| BRT + COLOR=RED | `color: error`, bold |
| DRK | element not rendered |
| HILIGHT=UNDERLINE | outlined/underlined TextField variant |
| FSET | value always sent (controlled component) |
