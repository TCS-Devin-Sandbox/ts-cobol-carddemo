# carddemo-trantype-ui

React 18 + TypeScript + MUI + React Router front end for the CTLI / CTTU replacement. Screen specification: `../docs/target-spec.md` §4.

## Prerequisites

Node 18+ (tested with Node 24) and a running `carddemo-trantype-api` on `http://localhost:8080` (see its README).

## Commands

```bash
npm ci
npm test            # Vitest + React Testing Library
npm run typecheck   # tsc
npm run build       # type-check + production bundle in dist/
npm run dev         # http://localhost:5173, proxies /api to http://localhost:8080 (override with API_URL)
```

Sign on with `admin` / `admin123` (ADMIN) to reach the admin menu; option 5 opens the list (CTLI), option 6 the maintenance screen (CTTU). `user` / `user123` demonstrates non-admin rejection.

## Routes

| Route | Screen |
|---|---|
| `/login` | Sign-on placeholder (COSGN00C) |
| `/admin` | Admin menu placeholder (CA00 / COADM01C) |
| `/transaction-types` | `TransactionTypeListPage` (CTLI, map CTRTLIA) |
| `/transaction-types/maintain` | `TransactionTypeEditPage` (CTTU, map CTRTUPA) |

Function keys are rendered as buttons with the BMS labels and are also bound to the keyboard (F2, F3, F4, F5, F7, F8, F10, F12, Enter).
