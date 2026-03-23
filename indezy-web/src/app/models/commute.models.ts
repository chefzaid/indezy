// Commute related interfaces and types

export type TravelMode = 'DRIVING' | 'TRANSIT';

export interface CommuteInfoDto {
  projectId: number;
  projectRole: string;
  clientName?: string;
  origin: string;
  destination: string;
  travelMode: TravelMode;
  durationInSeconds?: number;
  durationText?: string;
  distanceInMeters?: number;
  distanceText?: string;
}

export interface ProjectCommuteDto {
  project: import('./project.models').ProjectDto;
  commute?: CommuteInfoDto;
}
