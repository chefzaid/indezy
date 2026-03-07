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
  projects?: any[]; // ProjectDto[] - avoiding circular dependency
  createdAt?: Date;
  updatedAt?: Date;
}

export interface CreateSourceDto {
  name: string;
  type: SourceType;
  link?: string;
  isListing?: boolean;
  popularityRating?: number;
  usefulnessRating?: number;
  notes?: string;
  freelanceId: number;
}

export interface UpdateSourceDto {
  id: number;
  name?: string;
  type?: SourceType;
  link?: string;
  isListing?: boolean;
  popularityRating?: number;
  usefulnessRating?: number;
  notes?: string;
}
