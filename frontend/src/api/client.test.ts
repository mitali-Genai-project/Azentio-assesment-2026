import { vi } from 'vitest';
import { ApiError, createApi } from './client';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('createApi', () => {
  it('sends basic auth and the AJAX marker header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { username: 'analyst', roles: ['ANALYST'] }));
    const api = createApi({ username: 'analyst', password: 'pässword' }, fetchMock);

    const user = await api.me();

    expect(user.username).toBe('analyst');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/me');
    const expectedAuth = 'Basic ' + btoa(String.fromCharCode(...new TextEncoder().encode('analyst:pässword')));
    expect(init.headers.Authorization).toBe(expectedAuth);
    expect(init.headers['X-Requested-With']).toBe('XMLHttpRequest');
  });

  it('posts a disposition as JSON', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { hitId: 'h1', status: 'ESCALATED' }));
    const api = createApi({ username: 'a', password: 'b' }, fetchMock);

    await api.dispose('h1', 'ESCALATED', 'Needs review');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/hits/h1/disposition');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ toStatus: 'ESCALATED', reason: 'Needs review' });
  });

  it('maps the backend error body to ApiError', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(409, {
      status: 409, error: 'INVALID_TRANSITION', message: 'A hit in status OPEN cannot be moved to CONFIRMED_MATCH',
    }));
    const api = createApi({ username: 'a', password: 'b' }, fetchMock);

    const error = await api.dispose('h1', 'CONFIRMED_MATCH', 'x').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.code).toBe('INVALID_TRANSITION');
    expect(error.message).toContain('cannot be moved');
  });

  it('copes with a non-JSON error body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 }));
    const api = createApi({ username: 'a', password: 'b' }, fetchMock);

    const error = await api.me().catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('HTTP_502');
  });
});
