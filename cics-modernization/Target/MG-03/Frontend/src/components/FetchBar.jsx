import { useState } from 'react';

/** "Enter User ID" + Fetch (ENTER) row of COUSR02/COUSR03. */
export default function FetchBar({ initial = '', busy, onFetch }) {
  const [id, setId] = useState(initial);
  return (
    <form className="flex gap-sp-3 items-end" onSubmit={(e) => { e.preventDefault(); onFetch(id); }}>
      <div className="form-group m-0">
        <label htmlFor="fetch-user-id">Enter user ID</label>
        <input id="fetch-user-id" value={id} maxLength={8} onChange={(e) => setId(e.target.value.toUpperCase())} />
      </div>
      <button type="submit" className="btn btn-secondary" disabled={busy}>Fetch</button>
    </form>
  );
}
