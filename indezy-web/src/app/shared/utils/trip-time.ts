import { CommuteInfoDto } from '../../models/commute.models';

/** A trip's time as minutes, or hours and minutes ("1 h 05"). */
export function tripTime(minutes: number): string {
  const rounded = Math.round(minutes);
  return rounded < 60 ? `${rounded} min` : `${Math.floor(rounded / 60)} h ${String(rounded % 60).padStart(2, '0')}`;
}

/** A trip from off-peak to rush hour ("≈ 50 min – 1 h 45"), one time when both match; "≈" unless measured. */
export function tripRange(minutes: number, rushMinutes: number, estimated = true): string {
  const prefix = estimated ? '≈ ' : '';
  return Math.round(minutes) === Math.round(rushMinutes) ? `${prefix}${tripTime(minutes)}`
    : `${prefix}${tripTime(minutes)} – ${tripTime(rushMinutes)}`;
}

/** An opportunity's trip from off-peak to rush hour, as its card shows it; null without a time. */
export function commuteRange(trip: CommuteInfoDto | undefined): string | null {
  const seconds = trip?.durationInSeconds;
  if (seconds === null || seconds === undefined) {
    return null;
  }
  return tripRange(seconds / 60, (trip!.rushDurationInSeconds ?? seconds) / 60, trip!.estimated === true);
}

/** An opportunity's distance one way ("≈ 23 km", or Google's own words); null without one. */
export function commuteDistance(trip: CommuteInfoDto | undefined): string | null {
  const meters = trip?.distanceInMeters;
  if (meters === null || meters === undefined) {
    return null;
  }
  return trip!.distanceText ?? `${trip!.estimated ? '≈ ' : ''}${Math.round(meters / 1000)} km`;
}
