import { FormEvent, useState } from 'react';
import { ACTION_LABELS } from '../policy';
import type { HitStatus } from '../types';

interface Props {
  toStatus: HitStatus;
  onSubmit: (reason: string) => Promise<void>;
  onCancel: () => void;
}

/** Asks for the mandatory reason before a disposition is sent (the server also rejects blank reasons). */
export function DispositionForm({ toStatus, onSubmit, onCancel }: Props) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const label = ACTION_LABELS[toStatus] ?? toStatus;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await onSubmit(reason.trim());
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="disposition" onSubmit={submit} aria-label={`${label} reason`}>
      <label>
        Reason for “{label}”
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2} maxLength={1000} autoFocus />
      </label>
      <div className="row">
        <button type="submit" className="primary" disabled={busy || reason.trim() === ''}>
          {busy ? 'Saving…' : `Confirm: ${label}`}
        </button>
        <button type="button" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  );
}
