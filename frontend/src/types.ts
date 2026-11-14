/** Mirrors the backend DTOs (com.azentio.clearlist.dto). */

export type Role = 'ANALYST' | 'SUPERVISOR';
export type HitStatus = 'OPEN' | 'ESCALATED' | 'FALSE_POSITIVE' | 'CONFIRMED_MATCH';
export type ListType = 'SANCTIONS' | 'PEP';
export type MatchedOn = 'primary' | 'alias';

export interface CurrentUser {
  username: string;
  roles: Role[];
}

export interface ReviewQueueItem {
  hitId: string;
  screeningId: string;
  customerRef: string;
  entryId: string;
  matchedName: string;
  matchedOn: MatchedOn;
  score: number;
  listType: ListType;
  listVersion: number;
  status: HitStatus;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface HitResponse {
  hitId: string;
  entryId: string;
  matchedName: string;
  score: number;
  matchedOn: MatchedOn;
  listType: ListType;
  status: HitStatus;
}

export interface AuditRecord {
  auditId: number;
  hitId: string;
  fromStatus: HitStatus;
  toStatus: HitStatus;
  actor: string;
  role: Role;
  reason: string;
  timestamp: string;
}

export interface ScreeningRequest {
  customerRef: string;
  fullName: string;
  dob?: string;
  nationality?: string;
}

export interface ScreeningResponse {
  screeningId: string;
  customerRef: string;
  listVersion: number;
  decision: 'CLEAR' | 'POTENTIAL_MATCH';
  totalHits: number;
  topHits: HitResponse[];
  cacheHit: boolean;
  screenedAt: string;
}
