import type { HitStatus, Role } from './types';

/**
 * Mirror of the backend's HitTransitionPolicy, used only to decide which buttons to show.
 * The server remains the authority: it re-checks every request (403/409) whatever the UI offers.
 */
const TRANSITIONS: Record<Role, Partial<Record<HitStatus, HitStatus[]>>> = {
  ANALYST: { OPEN: ['ESCALATED', 'FALSE_POSITIVE'] },
  SUPERVISOR: { ESCALATED: ['CONFIRMED_MATCH', 'FALSE_POSITIVE'] },
};

export function allowedTransitions(roles: Role[], status: HitStatus): HitStatus[] {
  const targets = roles.flatMap((role) => TRANSITIONS[role][status] ?? []);
  return [...new Set(targets)];
}

/** Button labels for the statuses a disposition can move a hit to (nothing ever moves back to OPEN). */
export const ACTION_LABELS: Partial<Record<HitStatus, string>> = {
  ESCALATED: 'Escalate',
  FALSE_POSITIVE: 'Mark false positive',
  CONFIRMED_MATCH: 'Confirm match',
};

export const STATUS_LABELS: Record<HitStatus, string> = {
  OPEN: 'Open',
  ESCALATED: 'Escalated',
  FALSE_POSITIVE: 'False positive',
  CONFIRMED_MATCH: 'Confirmed match',
};
