import { StepStatus } from '../../models';
import { monthGrid, monthKey, monthOf, upcoming } from './calendar-month';

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

  it('lists the planned steps and the calls not cancelled from now on, soonest first', () => {
    const now = new Date(2026, 9, 20, 12);
    const call = (id: number, startsAt: Date, cancelledAt: string | null = null) => ({ id, name: `Caller ${id}`,
      startsAt: startsAt.toISOString(), endsAt: startsAt.toISOString(), email: 'c@esn.example', company: null, message: null, cancelledAt });
    const steps = [step(1, '2026-10-22T09:00:00'), step(2, '2026-10-20T11:00:00'), step(3), { ...step(4, '2026-10-21T09:00:00'),
      status: StepStatus.VALIDATED }, step(5, '2026-10-20T15:00:00')];
    const calls = [call(8, new Date(2026, 9, 21, 10)), call(9, new Date(2026, 9, 20, 9)), call(10, new Date(2026, 9, 21, 11), '2026-10-19T08:00:00Z')];

    expect(upcoming(steps, calls, now).map(item => item.key)).toEqual(['step-5', 'call-8', 'step-1']);
    expect(upcoming([], [], now)).toEqual([]);
  });
});
