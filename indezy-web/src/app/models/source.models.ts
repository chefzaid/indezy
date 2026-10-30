// Source related interfaces and types

export type SourceType = 'JOB_BOARD' | 'SOCIAL_MEDIA' | 'EMAIL' | 'CALL' | 'SMS';

export interface SourceDto {
  id?: number;
  name: string;
  type: SourceType;
  link?: string;
  isListing?: boolean;
  popularityRating?: number;
  usefulnessRating?: number;
  notes?: string;
  freelanceId: number;
  totalProjects?: number;
  averageDailyRate?: number;
  createdAt?: Date;
  updatedAt?: Date;
}

