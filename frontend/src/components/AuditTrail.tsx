import { useEffect, useState } from 'react';
import type { Api } from '../api/client';
import { STATUS_LABELS } from '../policy';
import type { AuditRecord } from '../types';

interface Props {
  api: Api;
  hitId: string;
  onClose: () => void;
}

export function AuditTrail({ api, hitId, onClose }: Props) {
  const [records, setRecords] = useState<AuditRecord[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api.audit(hitId)
      .then((result) => active && setRecords(result))
      .catch((e: Error) => active && setError(e.message));
    return () => {
      active = false;
    };
  }, [api, hitId]);

  return (
    <section className="card audit" aria-label="Audit history">
      <div className="row space-between">
        <h2>Audit history</h2>
        <button type="button" onClick={onClose}>Close</button>
      </div>
      {error && <p role="alert" className="error">{error}</p>}
      {!error && records === null && <p className="muted">Loading…</p>}
      {records?.length === 0 && <p className="muted">No dispositions yet.</p>}
      {records && records.length > 0 && (
        <ol className="timeline">
          {records.map((r) => (
            <li key={r.auditId}>
              <strong>{STATUS_LABELS[r.fromStatus]} → {STATUS_LABELS[r.toStatus]}</strong>
              <span className="muted"> by {r.actor} ({r.role}) · {new Date(r.timestamp).toLocaleString()}</span>
              <p>{r.reason}</p>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
