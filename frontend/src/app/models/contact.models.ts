// Contact related interfaces and types

export interface ContactDto {
  id?: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  position: string;
  clientId: number;
  clientName?: string;
  freelanceId: number;
  fullName?: string;
  notes?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  createdAt?: Date;
  updatedAt?: Date;
}

export interface CreateContactDto {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  position: string;
  clientId: number;
  freelanceId: number;
  notes?: string;
  status?: 'ACTIVE' | 'INACTIVE';
}

export interface UpdateContactDto {
  id: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  position?: string;
  clientId?: number;
  notes?: string;
  status?: 'ACTIVE' | 'INACTIVE';
}
