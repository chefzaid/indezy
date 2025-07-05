import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, delay, map } from 'rxjs';

export interface ContactDto {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  position: string;
  clientId: number;
  clientName: string;
  notes?: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: Date;
  updatedAt: Date;
}

@Injectable({
  providedIn: 'root'
})
export class ContactService {
  private apiUrl = '/api/contacts';

  constructor(private http: HttpClient) {}

  // Mock data for development
  private mockContacts: ContactDto[] = [
    {
      id: 1,
      firstName: 'Marie',
      lastName: 'Dubois',
      email: 'marie.dubois@techcorp.fr',
      phone: '+33 1 23 45 67 89',
      position: 'Directrice RH',
      clientId: 1,
      clientName: 'TechCorp Solutions',
      notes: 'Contact principal pour les recrutements développeurs',
      status: 'ACTIVE',
      createdAt: new Date('2024-01-15'),
      updatedAt: new Date('2024-01-15')
    },
    {
      id: 2,
      firstName: 'Pierre',
      lastName: 'Martin',
      email: 'p.martin@innovate.com',
      phone: '+33 1 98 76 54 32',
      position: 'CTO',
      clientId: 2,
      clientName: 'Innovate Digital',
      notes: 'Décideur technique, préfère les entretiens le matin',
      status: 'ACTIVE',
      createdAt: new Date('2024-02-01'),
      updatedAt: new Date('2024-02-10')
    },
    {
      id: 3,
      firstName: 'Sophie',
      lastName: 'Leroy',
      email: 'sophie.leroy@startup.io',
      phone: '+33 6 12 34 56 78',
      position: 'Founder & CEO',
      clientId: 3,
      clientName: 'StartupBoost',
      notes: 'Très réactive, privilégier les échanges par email',
      status: 'ACTIVE',
      createdAt: new Date('2024-02-15'),
      updatedAt: new Date('2024-02-15')
    },
    {
      id: 4,
      firstName: 'Jean',
      lastName: 'Moreau',
      email: 'j.moreau@consulting.fr',
      phone: '+33 1 45 67 89 01',
      position: 'Senior Manager',
      clientId: 4,
      clientName: 'Consulting Pro',
      notes: 'Contact pour missions de conseil en transformation digitale',
      status: 'INACTIVE',
      createdAt: new Date('2024-01-20'),
      updatedAt: new Date('2024-03-01')
    }
  ];

  getContacts(): Observable<ContactDto[]> {
    return of(this.mockContacts).pipe(delay(500));
  }

  getContact(id: number): Observable<ContactDto | undefined> {
    const contact = this.mockContacts.find(c => c.id === id);
    return of(contact).pipe(delay(300));
  }

  createContact(contact: Omit<ContactDto, 'id' | 'createdAt' | 'updatedAt'>): Observable<ContactDto> {
    const newContact: ContactDto = {
      ...contact,
      id: Math.max(...this.mockContacts.map(c => c.id)) + 1,
      createdAt: new Date(),
      updatedAt: new Date()
    };
    this.mockContacts.push(newContact);
    return of(newContact).pipe(delay(500));
  }

  updateContact(id: number, contact: Partial<ContactDto>): Observable<ContactDto> {
    const index = this.mockContacts.findIndex(c => c.id === id);
    if (index !== -1) {
      this.mockContacts[index] = {
        ...this.mockContacts[index],
        ...contact,
        updatedAt: new Date()
      };
      return of(this.mockContacts[index]).pipe(delay(500));
    }
    throw new Error('Contact not found');
  }

  deleteContact(id: number): Observable<void> {
    const index = this.mockContacts.findIndex(c => c.id === id);
    if (index !== -1) {
      this.mockContacts.splice(index, 1);
    }
    return of(void 0).pipe(delay(300));
  }

  searchContacts(query: string): Observable<ContactDto[]> {
    const filtered = this.mockContacts.filter(contact =>
      contact.firstName.toLowerCase().includes(query.toLowerCase()) ||
      contact.lastName.toLowerCase().includes(query.toLowerCase()) ||
      contact.email.toLowerCase().includes(query.toLowerCase()) ||
      contact.position.toLowerCase().includes(query.toLowerCase()) ||
      contact.clientName.toLowerCase().includes(query.toLowerCase())
    );
    return of(filtered).pipe(delay(300));
  }

  getContactsByClient(clientId: number): Observable<ContactDto[]> {
    console.log('ContactService.getContactsByClient called with clientId:', clientId);
    const filtered = this.mockContacts.filter(contact => contact.clientId === clientId);
    console.log('Found contacts for client:', filtered.length);
    return of(filtered).pipe(delay(300));
  }

  filterContacts(filters: {
    status?: string;
    clientId?: number;
  }): Observable<ContactDto[]> {
    let filtered = [...this.mockContacts];

    if (filters.status) {
      filtered = filtered.filter(contact => contact.status === filters.status);
    }

    if (filters.clientId) {
      filtered = filtered.filter(contact => contact.clientId === filters.clientId);
    }

    return of(filtered).pipe(delay(300));
  }
}
