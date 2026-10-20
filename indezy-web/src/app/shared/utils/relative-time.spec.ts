import { relativeDay, relativeTime } from './relative-time';

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

describe('relativeDay', () => {
  const evening = new Date(2026, 9, 20, 23, 30);

  it('counts calendar days whatever the time of day', () => {
    expect(relativeDay('2026-10-20', 'en-US', evening)).toBe('today');
    expect(relativeDay('2026-10-19', 'en-US', evening)).toBe('yesterday');
    expect(relativeDay('2026-10-17', 'fr-FR', evening)).toBe('il y a 3 jours');
    expect(relativeDay('2026-10-06', 'en-US', evening)).toBe('2 weeks ago');
    expect(relativeDay('2026-08-01', 'en-US', evening)).toBe('2 months ago');
    expect(relativeDay('2026-10-21', 'en-US', evening)).toBe('tomorrow');
  });
});
