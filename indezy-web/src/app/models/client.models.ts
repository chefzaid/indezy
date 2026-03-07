// Client related interfaces and types

export type ClientStatus = 'ACTIVE' | 'INACTIVE' | 'ESN';

export interface ClientDto {
  id: number;
  companyName: string;
  address?: string;
  city: string;
  domain?: string;
  isFinal: boolean;
  notes?: string;
  freelanceId?: number;
  totalProjects?: number;
  totalContacts?: number;
  averageProjectRating?: number;
  // Legacy properties for compatibility
  name?: string;
  industry?: string;
  website?: string;
  status?: ClientStatus;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface CreateClientDto {
  companyName: string;
  address?: string;
  city: string;
  domain?: string;
  isFinal: boolean;
  notes?: string;
  freelanceId?: number;
  // Legacy properties for compatibility
  name?: string;
  industry?: string;
  website?: string;
  status?: ClientStatus;
}

export interface UpdateClientDto extends Partial<CreateClientDto> {
  id: number;
}
