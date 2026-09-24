/** A job-hunting season: a bounded prospection period with its own pipeline and dashboard. */
export interface Season {
  id?: number;
  name: string;
  /** ISO date (yyyy-MM-dd). */
  startDate: string;
  /** ISO date (yyyy-MM-dd); absent while the season is still running. */
  endDate?: string | null;
  objective?: string | null;
  targetDailyRate?: number | null;
  freelanceId?: number;
  /** Whether the season covers today (computed by the backend). */
  active?: boolean;
  projectCount?: number;
}
