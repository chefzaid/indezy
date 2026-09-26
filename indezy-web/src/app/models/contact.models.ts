// Contact related interfaces and types

export interface ContactDto {
  id?: number;
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  clientId: number;
  clientName?: string;
  freelanceId?: number;
  fullName?: string;
  notes?: string;
  createdAt?: Date;
  updatedAt?: Date;
}

