import { vi } from 'vitest';
import type { Api } from '../api/client';
import type { Page, ReviewQueueItem } from '../types';

export function hit(overrides: Partial<ReviewQueueItem> = {}): ReviewQueueItem {
  return {
    hitId: 'hit-1',
    screeningId: 'scr-1',
    customerRef: 'CUST-001',
    entryId: 'WL-00042',
    matchedName: 'Aleksandr Ivanovich Petrov',
    matchedOn: 'primary',
    score: 0.93,
    listType: 'SANCTIONS',
    listVersion: 3,
    status: 'OPEN',
    createdAt: '2026-11-14T08:00:00Z',
    ...overrides,
  };
}

export function page(items: ReviewQueueItem[]): Page<ReviewQueueItem> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

/** An Api whose methods are vi.fn() mocks; override the ones a test cares about. */
export function fakeApi(overrides: Partial<Record<keyof Api, unknown>> = {}) {
  return {
    me: vi.fn(),
    reviewQueue: vi.fn().mockResolvedValue(page([])),
    dispose: vi.fn(),
    audit: vi.fn().mockResolvedValue([]),
    screen: vi.fn(),
    ...overrides,
  } as unknown as Api & { [K in keyof Api]: ReturnType<typeof vi.fn> };
}
