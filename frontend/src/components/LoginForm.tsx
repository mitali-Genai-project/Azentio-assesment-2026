import { FormEvent, useState } from 'react';
import { ApiError, createApi, type Api } from '../api/client';
import type { CurrentUser } from '../types';

interface Props {
  onLogin: (api: Api, user: CurrentUser) => void;
  createClient?: typeof createApi;
}

export function LoginForm({ onLogin, createClient = createApi }: Props) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const api = createClient({ username, password });
    try {
      onLogin(api, await api.me());
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401
        ? 'Invalid username or password.'
        : 'Could not reach the Clearlist API.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card login" onSubmit={submit} aria-label="Sign in">
      <h1>Clearlist</h1>
      <p className="muted">Sanctions &amp; PEP screening review</p>
      <label>
        Username
        <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
      </label>
      <label>
        Password
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />
      </label>
      {error && <p role="alert" className="error">{error}</p>}
      <button type="submit" className="primary" disabled={busy}>
        {busy ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  );
}
