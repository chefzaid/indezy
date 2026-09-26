// Freelance related interfaces and types

export type FreelanceStatus = 'AVAILABLE' | 'EMPLOYED' | 'UNAVAILABLE';

export interface FreelanceDto {
  id?: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  status: FreelanceStatus;
  noticePeriodInDays?: number;
  availabilityDate?: string;
  reversionRate?: number;
  incomeTaxRate?: number;
  cvFilePath?: string;
  fullName?: string;
  totalProjects?: number;
  averageDailyRate?: number;
}

