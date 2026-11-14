import { Fragment, useCallback, useEffect, useState } from 'react';
import type { Api } from '../api/client';
import { ACTION_LABELS, STATUS_LABELS, allowedTransitions } from '../policy';
import type { HitStatus, Page, ReviewQueueItem, Role } from '../types';
import { AuditTrail } from './AuditTrail';
import { DispositionForm } from './DispositionForm';

const STATUSES: HitStatus[] = ['OPEN', 'ESCALATED', 'FALSE_POSITIVE', 'CONFIRMED_MATCH'];
const PAGE_SIZE = 20;

interface Props {
  api: Api;
  roles: Role[];
  /** Changes whenever new hits may exist (e.g. after a screening), forcing a reload. */
  refreshKey?: number;
}

interface PendingAction {
  hitId: string;
  toStatus: HitStatus;
}

export function ReviewQueue({ api, roles, refreshKey = 0 }: Props) {
  const [status, setStatus] = useState<HitStatus>('OPEN');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<ReviewQueueItem> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [auditHitId, setAuditHitId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setError(null);
      setData(await api.reviewQueue(status, page, PAGE_SIZE));
    } catch (e) {
      setError((e as Error).message);
    }
  }, [api, status, page]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  function changeStatus(next: HitStatus) {
    setStatus(next);
    setPage(0);
    setPending(null);
    setNotice(null);
  }

  async function dispose(hit: ReviewQueueItem, toStatus: HitStatus, reason: string) {
    try {
      await api.dispose(hit.hitId, toStatus, reason);
      setPending(null);
      setNotice(`${hit.customerRef}: hit moved to ${STATUS_LABELS[toStatus]}.`);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <section className="card" aria-label="Review queue">
      <div className="row space-between">
        <h2>Review queue</h2>
        <div className="tabs" role="tablist">
          {STATUSES.map((s) => (
            <button
              key={s}
              role="tab"
              aria-selected={s === status}
              className={s === status ? 'tab active' : 'tab'}
              onClick={() => changeStatus(s)}
            >
              {STATUS_LABELS[s]}
            </button>
          ))}
        </div>
      </div>

      {notice && <p role="status" className="notice">{notice}</p>}
      {error && <p role="alert" className="error">{error}</p>}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Customer</th>
              <th>Matched entry</th>
              <th>Score</th>
              <th>List</th>
              <th>Screened</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.length === 0 && (
              <tr>
                <td colSpan={7} className="muted center">No {STATUS_LABELS[status].toLowerCase()} hits.</td>
              </tr>
            )}
            {data?.content.map((hit) => (
              <Fragment key={hit.hitId}>
                <tr data-testid={`hit-${hit.hitId}`}>
                  <td>{hit.customerRef}</td>
                  <td>
                    {hit.matchedName}
                    <div className="muted small">{hit.entryId} · {hit.matchedOn}</div>
                  </td>
                  <td><span className={`score ${scoreClass(hit.score)}`}>{Math.round(hit.score * 100)}%</span></td>
                  <td><span className="pill">{hit.listType}</span> <span className="muted small">v{hit.listVersion}</span></td>
                  <td className="small">{new Date(hit.createdAt).toLocaleString()}</td>
                  <td><span className={`status ${hit.status.toLowerCase()}`}>{STATUS_LABELS[hit.status]}</span></td>
                  <td className="actions">
                    {allowedTransitions(roles, hit.status).map((target) => (
                      <button key={target} onClick={() => setPending({ hitId: hit.hitId, toStatus: target })}>
                        {ACTION_LABELS[target]}
                      </button>
                    ))}
                    <button className="link" onClick={() => setAuditHitId(hit.hitId)}>History</button>
                  </td>
                </tr>
                {pending?.hitId === hit.hitId && (
                  <tr>
                    <td colSpan={7}>
                      <DispositionForm
                        toStatus={pending.toStatus}
                        onSubmit={(reason) => dispose(hit, pending.toStatus, reason)}
                        onCancel={() => setPending(null)}
                      />
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="row pager">
          <button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span>Page {page + 1} of {data.totalPages} ({data.totalElements} hits)</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}

      {auditHitId && <AuditTrail api={api} hitId={auditHitId} onClose={() => setAuditHitId(null)} />}
    </section>
  );
}

function scoreClass(score: number): string {
  if (score >= 0.95) return 'high';
  if (score >= 0.9) return 'medium';
  return 'low';
}
