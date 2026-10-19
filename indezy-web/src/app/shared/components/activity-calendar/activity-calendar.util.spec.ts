import { ActivityReportDay, PublicHoliday } from '../../../models/activity-report.models';
import {
  applyBrush,
  brushOf,
  fillBusinessDays,
  isWeekend,
  isWithinMission,
  monthDates,
  monthWeeks,
  totals
} from './activity-calendar.util';

describe('activity calendar util', () => {
  const novemberHolidays: PublicHoliday[] = [
    { date: '2026-11-01', holiday: 'ALL_SAINTS' },
    { date: '2026-11-11', holiday: 'ARMISTICE' }
  ];

  it('lists the ISO dates of a month, leap years included', () => {
    expect(monthDates(2026, 11).length).toBe(30);
    expect(monthDates(2026, 11)[0]).toBe('2026-11-01');
    expect(monthDates(2026, 11)[29]).toBe('2026-11-30');
    expect(monthDates(2028, 2).length).toBe(29);
    expect(monthDates(2026, 2).length).toBe(28);
  });

  it('lays the month out in weeks starting on Monday', () => {
    // 1 November 2026 is a Sunday: six empty days before it.
    const weeks = monthWeeks(2026, 11);
    expect(weeks.length).toBe(6);
    expect(weeks[0]).toEqual([null, null, null, null, null, null, '2026-11-01']);
    expect(weeks[1][0]).toBe('2026-11-02');
    expect(weeks[5]).toEqual(['2026-11-30', null, null, null, null, null, null]);
    // 1 February 2027 is a Monday: four full weeks.
    expect(monthWeeks(2027, 2).length).toBe(4);
    expect(monthWeeks(2027, 2)[0][0]).toBe('2027-02-01');
  });

  it('tells weekends apart whatever the browser time zone', () => {
    expect(isWeekend('2026-11-01')).toBeTrue();
    expect(isWeekend('2026-11-07')).toBeTrue();
    expect(isWeekend('2026-11-02')).toBeFalse();
  });

  it('keeps dates within the known mission dates', () => {
    expect(isWithinMission('2026-11-02')).toBeTrue();
    expect(isWithinMission('2026-11-02', '2026-11-03')).toBeFalse();
    expect(isWithinMission('2026-11-03', '2026-11-03', '2026-11-03')).toBeTrue();
    expect(isWithinMission('2026-11-04', undefined, '2026-11-03')).toBeFalse();
  });

  it('records a brush on a day, replaces another entry, and removes it when applied again or cleared', () => {
    let days: ActivityReportDay[] = applyBrush([], '2026-11-03', 'ONSITE');
    days = applyBrush(days, '2026-11-02', 'HALF_REMOTE');
    expect(days).toEqual([
      { date: '2026-11-02', type: 'REMOTE', halfDay: true },
      { date: '2026-11-03', type: 'ONSITE', halfDay: false }
    ]);
    days = applyBrush(days, '2026-11-03', 'HALF_ABSENCE');
    expect(days[1]).toEqual({ date: '2026-11-03', type: 'ABSENCE', halfDay: true });
    expect(applyBrush(days, '2026-11-03', 'HALF_ABSENCE').length).toBe(1);
    expect(applyBrush(days, '2026-11-02', 'CLEAR')).toEqual([{ date: '2026-11-03', type: 'ABSENCE', halfDay: true }]);
    expect(applyBrush(days, '2026-11-02', 'REMOTE')[0]).toEqual({ date: '2026-11-02', type: 'REMOTE', halfDay: false });
  });

  it('fills the empty business days within the mission, skipping weekends, holidays and recorded days', () => {
    const recorded: ActivityReportDay[] = [{ date: '2026-11-03', type: 'ABSENCE', halfDay: false }];
    const filled = fillBusinessDays(recorded, 2026, 11, novemberHolidays, 'REMOTE', '2026-11-02', '2026-11-13');

    expect(filled.map(day => day.date)).toEqual([
      '2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05', '2026-11-06',
      '2026-11-09', '2026-11-10', '2026-11-12', '2026-11-13'
    ]);
    expect(filled[1]).toEqual(recorded[0]);
    expect(filled[0]).toEqual({ date: '2026-11-02', type: 'REMOTE', halfDay: false });
    expect(fillBusinessDays([], 2026, 11, novemberHolidays, 'ONSITE').length).toBe(20);
  });

  it('adds up worked, on-site, remote and absent days with half days', () => {
    expect(totals([
      { date: '2026-11-02', type: 'ONSITE', halfDay: false },
      { date: '2026-11-03', type: 'REMOTE', halfDay: true },
      { date: '2026-11-04', type: 'ABSENCE', halfDay: true },
      { date: '2026-11-05', type: 'ONSITE', halfDay: true }
    ])).toEqual({ worked: 2, onsite: 1.5, remote: 0.5, absence: 0.5 });
    expect(totals([])).toEqual({ worked: 0, onsite: 0, remote: 0, absence: 0 });
  });

  it('names the brush of a recorded day', () => {
    expect(brushOf({ date: '2026-11-02', type: 'REMOTE', halfDay: true })).toBe('HALF_REMOTE');
    expect(brushOf({ date: '2026-11-02', type: 'ABSENCE', halfDay: false })).toBe('ABSENCE');
  });
});
