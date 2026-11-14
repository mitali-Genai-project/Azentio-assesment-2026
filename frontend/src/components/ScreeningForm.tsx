import { FormEvent, useState } from 'react';
import type { Api } from '../api/client';
import { ApiError } from '../api/client';
import type { ScreeningResponse } from '../types';

interface Props {
  api: Api;
  onScreened: () => void;
}

const EMPTY = { customerRef: '', fullName: '', dob: '', nationality: '' };

export function ScreeningForm({ api, onScreened }: Props) {
  const [form, setForm] = useState(EMPTY);
  const [result, setResult] = useState<ScreeningResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function update(field: keyof typeof EMPTY, value: string) {
    setForm({ ...form, [field]: value });
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const response = await api.screen({
        customerRef: form.customerRef.trim(),
        fullName: form.fullName.trim(),
        dob: form.dob || undefined,
        nationality: form.nationality.trim().toUpperCase() || undefined,
      });
      setResult(response);
      onScreened();
    } catch (e) {
      const details = e instanceof ApiError && e.details.length > 0
        ? `: ${e.details.map((d) => `${d.location} ${d.message}`).join('; ')}`
        : '';
      setError(`${(e as Error).message}${details}`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card" aria-label="New screening">
      <h2>New screening</h2>
      <form className="grid" onSubmit={submit}>
        <label>
          Customer ref
          <input value={form.customerRef} onChange={(e) => update('customerRef', e.target.value)} required />
        </label>
        <label>
          Full name
          <input value={form.fullName} onChange={(e) => update('fullName', e.target.value)} required />
        </label>
        <label>
          Date of birth
          <input type="date" value={form.dob} onChange={(e) => update('dob', e.target.value)} />
        </label>
        <label>
          Nationality
          <input value={form.nationality} onChange={(e) => update('nationality', e.target.value)}
                 maxLength={2} placeholder="RU" />
        </label>
        <button type="submit" className="primary" disabled={busy}>{busy ? 'Screening…' : 'Screen'}</button>
      </form>
      {error && <p role="alert" className="error">{error}</p>}
      {result && (
        <div className="result" role="status">
          <strong className={result.decision === 'CLEAR' ? 'clear' : 'flagged'}>
            {result.decision === 'CLEAR' ? 'Clear' : 'Potential match'}
          </strong>{' '}
          against list version {result.listVersion}
          {result.totalHits > 0 && ` · ${result.totalHits} hit(s) added to the review queue`}
          {result.cacheHit && <span className="muted small"> (cached result)</span>}
        </div>
      )}
    </section>
  );
}
