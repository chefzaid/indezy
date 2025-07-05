import { Injectable } from '@angular/core';
import { Observable, of, BehaviorSubject } from 'rxjs';
import { delay, map } from 'rxjs/operators';

export interface ClientDto {
  id: number;
  name: string;
  industry: string;
  address?: string;
  website?: string;
  notes?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'PROSPECT';
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateClientDto {
  name: string;
  industry: string;
  address?: string;
  website?: string;
  notes?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'PROSPECT';
}

export interface UpdateClientDto extends Partial<CreateClientDto> {
  id: number;
}

@Injectable({
  providedIn: 'root'
})
export class ClientService {
  private clients$ = new BehaviorSubject<ClientDto[]>([]);
  private nextId = 1;

  constructor() {
    // Initialize with mock data
    this.initializeMockData();
  }

  private initializeMockData(): void {
    const mockClients: ClientDto[] = [
      {
        id: 1,
        name: 'TechCorp Solutions',
        industry: 'Technology',
        address: '123 Avenue des Champs-Élysées, 75008 Paris',
        website: 'https://techcorp.fr',
        notes: 'Client principal, projets récurrents',
        status: 'ACTIVE',
        createdAt: new Date('2024-01-15'),
        updatedAt: new Date('2024-06-20')
      },
      {
        id: 2,
        name: 'StartupInnovate',
        industry: 'Fintech',
        address: '45 Rue de Rivoli, 75001 Paris',
        website: 'https://startupinnovate.com',
        notes: 'Startup prometteuse, budget limité',
        status: 'ACTIVE',
        createdAt: new Date('2024-02-10'),
        updatedAt: new Date('2024-06-15')
      },
      {
        id: 3,
        name: 'E-Commerce Plus',
        industry: 'E-commerce',
        address: '78 Boulevard Saint-Germain, 75006 Paris',
        status: 'PROSPECT',
        createdAt: new Date('2024-06-01'),
        updatedAt: new Date('2024-06-01')
      },
      {
        id: 4,
        name: 'ConsultingPro',
        industry: 'Consulting',
        address: '12 Place Vendôme, 75001 Paris',
        notes: 'Client exigeant mais bien payeur',
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
    console.log('ClientService.getClient called with id:', id);
    return this.clients$.pipe(
      map(clients => {
        console.log('Available clients:', clients.map(c => ({ id: c.id, name: c.name })));
        const foundClient = clients.find(client => client.id === id);
        console.log('Found client:', foundClient ? { id: foundClient.id, name: foundClient.name } : 'null');
        return foundClient;
      }),
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
          client.name.toLowerCase().includes(query.toLowerCase()) ||
          client.industry.toLowerCase().includes(query.toLowerCase()) ||
          (client.notes && client.notes.toLowerCase().includes(query.toLowerCase()))
        )
      ),
      delay(300)
    );
  }

  getClientsByStatus(status: 'ACTIVE' | 'INACTIVE' | 'PROSPECT'): Observable<ClientDto[]> {
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
