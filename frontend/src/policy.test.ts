import { allowedTransitions } from './policy';

describe('allowedTransitions (mirror of the backend HitTransitionPolicy)', () => {
  it.each([
    [['ANALYST'], 'OPEN', ['ESCALATED', 'FALSE_POSITIVE']],
    [['ANALYST'], 'ESCALATED', []],
    [['SUPERVISOR'], 'OPEN', []],
    [['SUPERVISOR'], 'ESCALATED', ['CONFIRMED_MATCH', 'FALSE_POSITIVE']],
    [['ANALYST', 'SUPERVISOR'], 'CONFIRMED_MATCH', []],
    [['ANALYST'], 'FALSE_POSITIVE', []],
  ] as const)('%j on %s -> %j', (roles, status, expected) => {
    expect(allowedTransitions([...roles], status)).toEqual(expected);
  });
});
