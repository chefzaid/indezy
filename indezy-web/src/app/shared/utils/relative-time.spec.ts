import { relativeTime } from './relative-time';

describe('relativeTime', () => {
  const now = new Date('2026-10-19T12:00:00Z');

  it('says how long ago a moment was in the locale', () => {
    expect(relativeTime('2026-10-19T11:59:30Z', 'en-US', now)).toBe('now');
    expect(relativeTime('2026-10-19T11:45:00Z', 'en-US', now)).toBe('15 minutes ago');
    expect(relativeTime('2026-10-19T09:00:00Z', 'fr-FR', now)).toBe('il y a 3 heures');
    expect(relativeTime('2026-10-18T10:00:00Z', 'en-US', now)).toBe('yesterday');
    expect(relativeTime('2026-10-05T12:00:00Z', 'en-US', now)).toBe('2 weeks ago');
    expect(relativeTime('2026-07-19T12:00:00Z', 'en-US', now)).toBe('3 months ago');
    expect(relativeTime(new Date('2024-10-19T12:00:00Z'), 'en-US', now)).toBe('2 years ago');
  });

  it('also reads moments to come', () => {
    expect(relativeTime('2026-10-20T12:00:00Z', 'en-US', now)).toBe('tomorrow');
  });
});
