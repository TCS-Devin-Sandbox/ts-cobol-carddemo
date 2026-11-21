# E2 — CC00 / CM00 / CA00

Source contracts: COSGN00C, COMEN01C, COADM01C; COSGN00 maps;
COMEN02Y/COADM02Y options; CSMSG01Y/COTTL01Y messages and titles.
No customer estate or golden data is modified.

## USRSEC mapping

Online Flyway V3 creates `user_security` (independent of shared VSAM V1).

| Business column | CSUSR01Y field | Target |
|---|---|---|
| user_id | SEC-USR-ID | CHAR(8), primary key |
| first_name | SEC-USR-FNAME | CHAR(20) |
| last_name | SEC-USR-LNAME | CHAR(20) |
| password_hash | SEC-USR-PWD | VARCHAR(100), BCrypt of uppercase password |
| user_type | SEC-USR-TYPE | CHAR(1), A or U |
| filler | SEC-USR-FILLER | CHAR(23), not null |

Startup inserts configured users if absent, uppercasing IDs/passwords, mapping
ADMIN to A and USER to U. Names and filler default to blank. Existing records
are never overwritten, including passwords. JWT_SECRET, ADMIN_PASSWORD and
USER_PASSWORD retain their environment configuration; dev additionally supplies
the synthetic ADMIN001/USER0001 users with PASSWORD.

## HTTP and session contract

POST `/api/auth/login` accepts `{userId,password}` and returns
`{token,userId,userType,roles,nextScreen}`. Empty ID is checked before empty
password. Missing/wrong credentials retain distinct literal legacy messages.
JWT contains sub=user ID, roles, user_type, from_tranid=CC00,
from_program=COSGN00C, issuer=carddemo-online-api, issued/expiry times.
Session storage `carddemo.session` holds token/userId/userType/roles.

GET `/api/menus/main` or `/api/menus/admin` returns tranId/program,
title01/title02 and numbered options (two-digit PIC 9(02), names PIC X(35)).
POST the same path plus `/select` with `{option}` returns a migrated route
or a literal message and severity error/info (DFHRED/DFHGREEN).
Only COTRTLIC and COTRTUPC have routes. CA00 and transaction-type routes
require ADMIN; CM00 requires authentication. F3 is client-side sign-off.

## Deviations and preserved quirks

* BCrypt replaces plaintext SEC-USR-PWD; uppercase before hashing/comparison
  preserves case-insensitive credentials. No plaintext password is persisted.
* Stateless JWT claims replace the sign-on COMMAREA identity/provenance;
  CDEMO-PGM-REENTER and CICS pseudo-conversation state are unnecessary.
* Missing programs use COADM01C's `This option is not installed ...` informational
  response on both menus rather than a main-menu unhandled PGMIDERR.
* Browser routing replaces XCTL and F3 CICS return; sign-on F3 displays the
  CSMSG01Y thank-you text without terminating a terminal session.
* JWT issuer is renamed from carddemo-trantype-api to carddemo-online-api.
* Preserved quirk: distinct missing-user/wrong-password messages disclose user
  existence. This is intentional legacy equivalence, not a recommended policy.
* Preserved quirks: DUMMY STRING stops at the first space and concatenates
  `is coming soon ...` without adding a space; COPAUS0C absence is red.
  Main option 8 remains type U as shipped, not the commented admin-only name.

## Paragraph traceability

| Program / paragraph | Test |
|---|---|
| COSGN00C MAIN-PARA, READ-USER-SEC-FILE (DB, success, claims) | TransactionTypeApiIntegrationTest.COSGN00C_MAIN_PARA_READ_USER_SEC_FILE_JWT_COMMAREA |
| COSGN00C PROCESS-ENTER-KEY, SEND-SIGNON-SCREEN | SignonMenuTest.COSGN00C_PROCESS_ENTER_KEY_SEND_SIGNON_SCREEN |
| COSGN00C READ-USER-SEC-FILE (missing, wrong, error) | SignonMenuTest.COSGN00C_READ_USER_SEC_FILE |
| COSGN00C SEND-PLAIN-TEXT, invalid AID | App.test.tsx SEND-PLAIN-TEXT |
| COMEN01C / COADM01C MAIN-PARA, BUILD-MENU-OPTIONS, POPULATE-HEADER-INFO | SignonMenuTest.COMEN01C_COADM01C_MAIN_PARA_BUILD_MENU_OPTIONS_POPULATE_HEADER_INFO |
| COMEN01C PROCESS-ENTER-KEY, PGMIDERR fallback | SignonMenuTest.COMEN01C_PROCESS_ENTER_KEY_PGMIDERR_ERR_PARA |
| COMEN01C PROCESS-ENTER-KEY DUMMY | SignonMenuTest.COMEN01C_PROCESS_ENTER_KEY_DUMMY_STRING |
| COADM01C PROCESS-ENTER-KEY, XCTL | SignonMenuTest.COADM01C_PROCESS_ENTER_KEY_XCTL |
| COMEN01C / COADM01C RETURN-TO-SIGNON-SCREEN | App.test.tsx PROCESS-ENTER-KEY / RETURN-TO-SIGNON-SCREEN (A and U) |

UI tests cover sign-on validation surface, both menu routes, select messages,
function keys and cleared sessions. API tests use MockMvc; the login integration
test uses PostgreSQL 15 Testcontainers and real BCrypt/JWT issuance.
