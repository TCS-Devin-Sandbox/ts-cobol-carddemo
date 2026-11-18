# carddemo-trantype-ui

React 18 + TypeScript (Vite, MUI, React Router) UI for the transaction-type screens. Spec: `../docs/target-spec.md` §3.

```sh
npm install
npm run dev        # http://localhost:5173, proxies /api to http://localhost:8080 (override with API_URL)
npm test           # Vitest + React Testing Library
npm run lint && npm run typecheck && npm run build
```

Routes: `/login`, `/admin` (admin menu placeholder: options 5 and 6), `/transaction-types` (CTLI), `/transaction-types/maintain` (CTTU). PF keys are buttons (`F2=Add`, `F3=Exit`, `F7=Page Up`, `F8=Page Dn`, `F10=Save`, `F4=Delete`, `F5=Save`, `F12=Cancel`).
