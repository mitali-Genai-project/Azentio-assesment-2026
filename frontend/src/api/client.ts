import type {
  AuditRecord,
  CurrentUser,
  HitResponse,
  HitStatus,
  Page,
  ReviewQueueItem,
  ScreeningRequest,
  ScreeningResponse,
} from '../types';

export interface Credentials {
  username: string;
  password: string;
}

export interface ErrorDetail {
  location: string;
  message: string;
}

/** A non-2xx response, carrying the backend's error code and message (see GlobalExceptionHandler). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details: ErrorDetail[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function toBase64(text: string): string {
  // btoa only accepts Latin-1, so encode as UTF-8 bytes first (passwords may contain any character)
  return btoa(String.fromCharCode(...new TextEncoder().encode(text)));
}

/**
 * Typed client for the Clearlist API. Credentials live only in this closure (memory), never in storage.
 * Requests are marked as AJAX so the server answers 401 without triggering the browser's login pop-up.
 */
export function createApi(credentials: Credentials, fetchImpl: typeof fetch = fetch) {
  const authorization = `Basic ${toBase64(`${credentials.username}:${credentials.password}`)}`;

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetchImpl(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        Authorization: authorization,
        'X-Requested-With': 'XMLHttpRequest',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      },
    });
    const text = await response.text();
    let body: unknown;
    try {
      body = text ? JSON.parse(text) : undefined;
    } catch {
      body = undefined; // e.g. an HTML error page from a proxy
    }
    if (!response.ok) {
      const error = (body ?? {}) as { error?: string; message?: string; details?: ErrorDetail[] };
      throw new ApiError(
        response.status,
        error.error ?? `HTTP_${response.status}`,
        error.message ?? (response.statusText || 'Request failed'),
        error.details ?? [],
      );
    }
    return body as T;
  }

  return {
    me: () => request<CurrentUser>('/api/me'),
    reviewQueue: (status: HitStatus, page: number, size: number) =>
      request<Page<ReviewQueueItem>>(
        `/api/hits?${new URLSearchParams({ status, page: String(page), size: String(size) })}`,
      ),
    dispose: (hitId: string, toStatus: HitStatus, reason: string) =>
      request<HitResponse>(`/api/hits/${encodeURIComponent(hitId)}/disposition`, {
        method: 'POST',
        body: JSON.stringify({ toStatus, reason }),
      }),
    audit: (hitId: string) => request<AuditRecord[]>(`/api/hits/${encodeURIComponent(hitId)}/audit`),
    screen: (screening: ScreeningRequest) =>
      request<ScreeningResponse>('/api/screenings', { method: 'POST', body: JSON.stringify(screening) }),
  };
}

export type Api = ReturnType<typeof createApi>;
