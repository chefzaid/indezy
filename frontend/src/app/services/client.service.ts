import { Injectable } from '@angular/core';
import { Observable, of, BehaviorSubject } from 'rxjs';
import { delay, map } from 'rxjs/operators';

export type ClientStatus = 'ACTIVE' | 'INACTIVE' | 'PROSPECT';

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

@Injectable({
  providedIn: 'root'
})
export class ClientService {
  private readonly clients$ = new BehaviorSubject<ClientDto[]>([]);
  private nextId = 1;

  constructor() {
    // Initialize with mock data
    this.initializeMockData();
  }

  private initializeMockData(): void {
    const mockClients: ClientDto[] = [
      {
        id: 1,
        companyName: 'TechCorp Solutions',
        city: 'Paris',
        address: '123 Avenue des Champs-Élysées, 75008 Paris',
        domain: 'https://techcorp.fr',
        isFinal: true,
        notes: 'Client principal, projets récurrents',
        totalProjects: 5,
        totalContacts: 3,
        averageProjectRating: 4.5,
        // Legacy properties for compatibility
        name: 'TechCorp Solutions',
        industry: 'Technology',
        website: 'https://techcorp.fr',
        status: 'ACTIVE',
        createdAt: new Date('2024-01-15'),
        updatedAt: new Date('2024-06-20')
      },
      {
        id: 2,
        companyName: 'StartupInnovate',
        city: 'Paris',
        address: '45 Rue de Rivoli, 75001 Paris',
        domain: 'https://startupinnovate.com',
        isFinal: false,
        notes: 'Startup prometteuse, budget limité',
        totalProjects: 2,
        totalContacts: 2,
        averageProjectRating: 4.0,
        // Legacy properties for compatibility
        name: 'StartupInnovate',
        industry: 'Fintech',
        website: 'https://startupinnovate.com',
        status: 'ACTIVE',
        createdAt: new Date('2024-02-10'),
        updatedAt: new Date('2024-06-15')
      },
      {
        id: 3,
        companyName: 'E-Commerce Plus',
        city: 'Paris',
        address: '78 Boulevard Saint-Germain, 75006 Paris',
        isFinal: false,
        totalProjects: 0,
        totalContacts: 1,
        // Legacy properties for compatibility
        name: 'E-Commerce Plus',
        industry: 'E-commerce',
        status: 'PROSPECT',
        createdAt: new Date('2024-06-01'),
        updatedAt: new Date('2024-06-01')
      },
      {
        id: 4,
        companyName: 'ConsultingPro',
        city: 'Paris',
        address: '12 Place Vendôme, 75001 Paris',
        isFinal: true,
        notes: 'Client exigeant mais bien payeur',
        totalProjects: 1,
        totalContacts: 2,
        averageProjectRating: 3.5,
        // Legacy properties for compatibility
        name: 'ConsultingPro',
        industry: 'Consulting',
        status: 'INACTIVE',
        createdAt: new Date('2023-12-01'),
        updatedAt: new Date('2024-03-15')
      }
    ];

    this.clients$.next(mockClients);
    this.nextId = Math.max(...mockClients.map(c => c.id)) + 1;
  }

  getClients(): Observable<ClientDto[]> {
    return this.clients$.asObservable();
  }

  getClient(id: number): Observable<ClientDto | undefined> {
    return this.clients$.pipe(
      map(clients => clients.find(client => client.id === id)),
      delay(300) // Simulate API delay
    );
  }

  createClient(clientData: CreateClientDto): Observable<ClientDto> {
    const newClient: ClientDto = {
      ...clientData,
      id: this.nextId++,
      createdAt: new Date(),
      updatedAt: new Date()
    };

    const currentClients = this.clients$.value;
    this.clients$.next([...currentClients, newClient]);

    return of(newClient).pipe(delay(500));
  }

  updateClient(clientData: UpdateClientDto): Observable<ClientDto> {
    const currentClients = this.clients$.value;
    const index = currentClients.findIndex(client => client.id === clientData.id);
    
    if (index === -1) {
      throw new Error('Client not found');
    }

    const updatedClient: ClientDto = {
      ...currentClients[index],
      ...clientData,
      updatedAt: new Date()
    };

    const updatedClients = [...currentClients];
    updatedClients[index] = updatedClient;
    this.clients$.next(updatedClients);

    return of(updatedClient).pipe(delay(500));
  }

  deleteClient(id: number): Observable<boolean> {
    const currentClients = this.clients$.value;
    const filteredClients = currentClients.filter(client => client.id !== id);
    
    if (filteredClients.length === currentClients.length) {
      throw new Error('Client not found');
    }

    this.clients$.next(filteredClients);
    return of(true).pipe(delay(300));
  }

  searchClients(query: string): Observable<ClientDto[]> {
    return this.clients$.pipe(
      map(clients =>
        clients.filter(client =>
          client.name?.toLowerCase().includes(query.toLowerCase()) ||
          client.industry?.toLowerCase().includes(query.toLowerCase()) ||
          client.notes?.toLowerCase().includes(query.toLowerCase())
        )
      ),
      delay(300)
    );
  }

  getClientsByStatus(status: ClientStatus): Observable<ClientDto[]> {
    return this.clients$.pipe(
      map(clients => clients.filter(client => client.status === status)),
      delay(300)
    );
  }

  getClientStats(): Observable<{total: number, active: number, inactive: number, prospects: number}> {
    return this.clients$.pipe(
      map(clients => ({
        total: clients.length,
        active: clients.filter(c => c.status === 'ACTIVE').length,
        inactive: clients.filter(c => c.status === 'INACTIVE').length,
        prospects: clients.filter(c => c.status === 'PROSPECT').length
      })),
      delay(200)
    );
  }
}
