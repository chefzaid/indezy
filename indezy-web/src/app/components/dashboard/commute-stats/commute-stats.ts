import { ProjectCommuteDto } from '../../../models/commute.models';

/** Days in a working month, the remote share's denominator. */
const WORKING_DAYS = 20;
/** Above this many hours a month there and back, a commute counts as long. */
export const LONG_COMMUTE_HOURS = 20;
const OPEN_STAGES = ['CONTACT', 'INTERVIEW', 'OFFER'];

export interface CommuteStats {
  /** Average monthly hours there and back of the open opportunities, null without any known. */
  pipelineHours: number | null;
  /** The same for the won missions. */
  wonHours: number | null;
  /** Share of the working days remote across the open and won ones, in whole percents. */
  remotePercent: number | null;
  /** Average daily rates of the long and the short commutes, both known or neither. */
  longRate: number | null;
  shortRate: number | null;
}

/** Commute figures of the pipeline from the sorted commute list (see docs/features.md#dashboard-and-analytics). */
export function commuteStats(results: ProjectCommuteDto[]): CommuteStats {
  const live = results.filter(result => result.commute && (OPEN_STAGES.includes(result.project.status ?? '') || result.project.status === 'WON'));
  const hours = (result: ProjectCommuteDto): number | null =>
    result.commute?.monthlySeconds === null || result.commute?.monthlySeconds === undefined ? null : result.commute.monthlySeconds / 3600;
  const average = (values: number[]): number | null => values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
  const known = live.filter(result => hours(result) !== null);
  const hoursOf = (stages: string[]): number | null => {
    const value = average(known.filter(result => stages.includes(result.project.status ?? '')).map(result => hours(result)!));
    return value === null ? null : Math.round(value * 10) / 10;
  };
  const remote = average(live.map(result => (WORKING_DAYS - result.commute!.onsiteDaysPerMonth) / WORKING_DAYS));
  const rated = known.filter(result => result.project.dailyRate);
  const longRate = average(rated.filter(result => hours(result)! > LONG_COMMUTE_HOURS).map(result => result.project.dailyRate));
  const shortRate = average(rated.filter(result => hours(result)! <= LONG_COMMUTE_HOURS).map(result => result.project.dailyRate));
  const both = longRate !== null && shortRate !== null;
  return {
    pipelineHours: hoursOf(OPEN_STAGES),
    wonHours: hoursOf(['WON']),
    remotePercent: remote === null ? null : Math.round(remote * 100),
    longRate: both ? Math.round(longRate) : null,
    shortRate: both ? Math.round(shortRate) : null
  };
}
