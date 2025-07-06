// Freelance related interfaces and types

export interface FreelanceDto {
  id?: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  status: 'AVAILABLE' | 'EMPLOYED' | 'UNAVAILABLE';
  noticePeriodInDays?: number;
  availabilityDate?: string;
  reversionRate?: number;
  cvFilePath?: string;
  fullName?: string;
  totalProjects?: number;
  averageDailyRate?: number;
}

export interface CreateFreelanceDto {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  status: 'AVAILABLE' | 'EMPLOYED' | 'UNAVAILABLE';
  noticePeriodInDays?: number;
  availabilityDate?: string;
  reversionRate?: number;
  cvFilePath?: string;
}

export interface UpdateFreelanceDto {
  id: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  status?: 'AVAILABLE' | 'EMPLOYED' | 'UNAVAILABLE';
  noticePeriodInDays?: number;
  availabilityDate?: string;
  reversionRate?: number;
  cvFilePath?: string;
}
