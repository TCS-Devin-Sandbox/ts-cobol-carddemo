# carddemo-online-ui

React 18 + TypeScript (Vite, MUI, React Router) UI for the transaction-type screens. Spec: `../docs/target-spec.md` §3.

```sh
npm install
npm run dev        # http://localhost:5173, proxies /api to http://localhost:8080 (override with API_URL)
npm test           # Vitest + React Testing Library
npm run lint && npm run typecheck && npm run build
```

Routes: `/login` (CC00), `/menu` (CM00), `/admin` (CA00), `/transaction-types` (CTLI), `/transaction-types/maintain` (CTTU). Sign-on sends `{userId,password}` and routes administrators to CA00, users to CM00. Dev users: admin/admin123, user/user123, ADMIN001/PASSWORD, USER0001/PASSWORD (case-insensitive). Session storage holds token/userId/userType/roles. F3 exits sign-on with a thank-you message and signs off from either menu. See [E2 contract](../docs/online/signon-menus.md).
