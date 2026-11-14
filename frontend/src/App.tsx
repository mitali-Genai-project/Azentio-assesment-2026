import { useState } from 'react';
import type { Api } from './api/client';
import { LoginForm } from './components/LoginForm';
import { ReviewQueue } from './components/ReviewQueue';
import { ScreeningForm } from './components/ScreeningForm';
import type { CurrentUser } from './types';

interface Session {
  api: Api;
  user: CurrentUser;
}

export function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  if (!session) {
    return (
      <main className="centered">
        <LoginForm onLogin={(api, user) => setSession({ api, user })} />
      </main>
    );
  }

  return (
    <main className="page">
      <header className="row space-between">
        <div>
          <h1>Sanctions &amp; PEP Screening</h1>
          <p className="muted">Screen customers and review potential watchlist matches</p>
        </div>
        <div className="row">
          <span className="user">
            {session.user.username} · {session.user.roles.join(', ')}
          </span>
          <button onClick={() => setSession(null)}>Sign out</button>
        </div>
      </header>
      <ScreeningForm api={session.api} onScreened={() => setRefreshKey((k) => k + 1)} />
      <ReviewQueue api={session.api} roles={session.user.roles} refreshKey={refreshKey} />
    </main>
  );
}
