// Commute related interfaces and types

export type TravelMode = 'DRIVING' | 'TRANSIT';

/** Why a mission's commute has no time. */
export type CommuteUnavailable = 'NO_HOME_ADDRESS' | 'NO_CLIENT_ADDRESS' | 'NO_API_KEY' | 'NOT_FOUND' | 'LOOKUP_FAILED';

/** One trip's time and distance, the days on site a month, the time it costs a month there and back; or why none. */
export interface CommuteInfoDto {
  projectId: number;
  projectRole: string;
  clientName?: string;
  origin: string;
  destination: string;
  travelMode: TravelMode;
  durationInSeconds?: number;
  durationText?: string;
  /** One trip at rush hour (a weekday at 8:30), when known: the monthly commute counts it. */
  rushDurationInSeconds?: number | null;
  distanceInMeters?: number;
  distanceText?: string;
  /** A fully remote mission: no commute. */
  remote: boolean;
  onsiteDaysPerMonth: number;
  /** The commute's time a month, there and back on each day on site. */
  monthlySeconds?: number | null;
  unavailable?: CommuteUnavailable | null;
  /** Estimated from the straight line between the addresses, without Google. */
  estimated?: boolean;
  /** Whether the trip at rush hour takes longer than the profile's longest for its mode; absent without both. */
  beyondMaximum?: boolean | null;
}

export interface ProjectCommuteDto {
  project: import('./project.models').ProjectDto;
  commute?: CommuteInfoDto;
}
