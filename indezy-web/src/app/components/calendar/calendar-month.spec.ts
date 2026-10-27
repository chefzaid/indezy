import { StepStatus } from '../../models';
import { monthGrid, monthKey, monthOf } from './calendar-month';

describe('calendar month', () => {
  const step = (id: number, date?: string) => ({ id, title: `Step ${id}`, status: StepStatus.PLANNED, date });

  it('lays out a month in weeks from Monday, with the days around it', () => {
    const weeks = monthGrid(new Date(2026, 9, 14), []);

    expect(weeks.length).toBe(5);
    expect(weeks[0][0].key).toBe('2026-09-28');
    expect(weeks[0][0].inMonth).toBeFalse();
    expect(weeks[0][3].key).toBe('2026-10-01');
    expect(weeks[4][6].key).toBe('2026-11-01');
    expect(monthGrid(new Date(2027, 1, 1), []).length).toBe(4);
    expect(monthGrid(new Date(2026, 2, 1), [])[5][0].key).toBe('2026-03-30');
  });

  it('puts each dated step on its day, by time, and leaves undated ones out', () => {
    const weeks = monthGrid(new Date(2026, 9, 1), [step(1, '2026-10-20T14:00:00'), step(2, '2026-10-20T09:30:00'), step(3)]);
    const day = weeks.flat().find(candidate => candidate.key === '2026-10-20')!;

    expect(day.steps.map(item => item.id)).toEqual([2, 1]);
    expect(weeks.flat().reduce((count, candidate) => count + candidate.steps.length, 0)).toBe(2);
  });

  it('puts each call booked on the day it starts, leaving cancelled ones out', () => {
    const call = (id: number, startsAt: string, cancelledAt: string | null = null) => ({ id, startsAt, endsAt: startsAt, name: 'Bob',
      email: 'bob@esn.example', company: null, message: null, cancelledAt });
    const weeks = monthGrid(new Date(2026, 9, 1), [], [call(1, new Date(2026, 9, 20, 15).toISOString()),
      call(2, new Date(2026, 9, 20, 10).toISOString()), call(3, new Date(2026, 9, 21, 10).toISOString(), '2026-10-19T08:00:00Z')]);
    const day = weeks.flat().find(candidate => candidate.key === '2026-10-20')!;

    expect(day.bookings.map(item => item.id)).toEqual([2, 1]);
    expect(weeks.flat().find(candidate => candidate.key === '2026-10-21')!.bookings).toEqual([]);
  });

  it('reads and writes a month in the page address', () => {
    expect(monthKey(monthOf('2026-03'))).toBe('2026-03');
    expect(monthKey(monthOf('march', new Date(2026, 9, 26)))).toBe('2026-10');
    expect(monthKey(monthOf(null, new Date(2026, 0, 31)))).toBe('2026-01');
  });
});
