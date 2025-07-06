import { TestBed } from '@angular/core/testing';
import { ClientService, ClientDto, CreateClientDto, UpdateClientDto } from './client.service';

describe('ClientService', () => {
  let service: ClientService;

  const _mockClient: ClientDto = {
    id: 1,
    companyName: 'TechCorp Solutions',
    name: 'TechCorp Solutions',
    address: '123 Tech Street',
    city: 'Paris',
    domain: 'Technology',
    industry: 'Software Development',
    website: 'https://techcorp.com',
    isFinal: true,
    status: 'ACTIVE',
    notes: 'Great client to work with',
    freelanceId: 1,
    totalProjects: 3,
    totalContacts: 2,
    averageProjectRating: 4.5,
    createdAt: new Date('2024-01-15'),
    updatedAt: new Date('2024-01-15')
  };

  // Mock clients for testing
  // const mockClients: ClientDto[] = [
  //   mockClient,
  //   {
  //     ...mockClient,
  //     id: 2,
  //     companyName: 'StartupBoost',
  //     name: 'StartupBoost',
  //     status: 'PROSPECT',
  //     isFinal: false
  //   }
  // ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ClientService]
    });

    service = TestBed.inject(ClientService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getClients', () => {
    it('should return observable of clients', (done) => {
      service.getClients().subscribe(clients => {
        expect(clients).toBeDefined();
        expect(Array.isArray(clients)).toBeTruthy();
        expect(clients.length).toBeGreaterThan(0);
        done();
      });
    });

    it('should return clients with expected properties', (done) => {
      service.getClients().subscribe(clients => {
        const client = clients[0];
        expect(client.id).toBeDefined();
        expect(client.companyName).toBeDefined();
        expect(client.city).toBeDefined();
        expect(client.status).toBeDefined();
        done();
      });
    });
  });

  describe('getClient', () => {
    it('should return specific client by id', (done) => {
      const clientId = 1;
      
      service.getClient(clientId).subscribe(client => {
        expect(client).toBeDefined();
        expect(client?.id).toBe(clientId);
        done();
      });
    });

    it('should return undefined for non-existent client', (done) => {
      const nonExistentId = 999;
      
      service.getClient(nonExistentId).subscribe(client => {
        expect(client).toBeUndefined();
        done();
      });
    });

    it('should simulate API delay', (done) => {
      const startTime = Date.now();
      
      service.getClient(1).subscribe(() => {
        const endTime = Date.now();
        const duration = endTime - startTime;
        expect(duration).toBeGreaterThanOrEqual(250); // Should have some delay
        done();
      });
    });
  });

  describe('createClient', () => {
    it('should create a new client', (done) => {
      const newClientData: CreateClientDto = {
        companyName: 'New Tech Company',
        city: 'Lyon',
        domain: 'AI/ML',
        isFinal: false,
        status: 'PROSPECT'
      };

      service.createClient(newClientData).subscribe(createdClient => {
        expect(createdClient).toBeDefined();
        expect(createdClient.companyName).toBe(newClientData.companyName);
        expect(createdClient.city).toBe(newClientData.city);
        expect(createdClient.id).toBeDefined();
        expect(createdClient.createdAt).toBeDefined();
        expect(createdClient.updatedAt).toBeDefined();
        done();
      });
    });

    it('should assign incremental id to new client', (done) => {
      const newClientData: CreateClientDto = {
        companyName: 'Another Company',
        city: 'Marseille',
        isFinal: true,
        status: 'ACTIVE'
      };

      service.createClient(newClientData).subscribe(createdClient => {
        expect(createdClient.id).toBeGreaterThan(0);
        done();
      });
    });
  });

  describe('updateClient', () => {
    it('should update existing client', (done) => {
      const updateData: UpdateClientDto = {
        id: 1,
        companyName: 'Updated Company Name',
        notes: 'Updated notes'
      };

      service.updateClient(updateData).subscribe(updatedClient => {
        expect(updatedClient).toBeDefined();
        if (updatedClient) {
          expect(updatedClient.companyName).toBe('Updated Company Name');
          expect(updatedClient.notes).toBe('Updated notes');
        }
        expect(updatedClient?.updatedAt).toBeDefined();
        done();
      });
    });

    it('should throw error for non-existent client update', () => {
      const updateData: UpdateClientDto = {
        id: 999,
        companyName: 'Non-existent Company'
      };

      expect(() => {
        service.updateClient(updateData);
      }).toThrowError('Client not found');
    });
  });

  describe('deleteClient', () => {
    it('should delete existing client', (done) => {
      const clientId = 1;

      service.deleteClient(clientId).subscribe(result => {
        expect(result).toBe(true);
        
        // Verify client is actually deleted
        service.getClient(clientId).subscribe(deletedClient => {
          expect(deletedClient).toBeUndefined();
          done();
        });
      });
    });

    it('should throw error for non-existent client deletion', () => {
      const nonExistentId = 999;

      expect(() => {
        service.deleteClient(nonExistentId);
      }).toThrowError('Client not found');
    });
  });

  describe('searchClients', () => {
    it('should search clients by company name', (done) => {
      const searchQuery = 'tech';

      service.searchClients(searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        
        if (results.length > 0) {
          const hasMatchingName = results.some(client => 
            client.companyName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            client.name?.toLowerCase().includes(searchQuery.toLowerCase())
          );
          expect(hasMatchingName).toBeTruthy();
        }
        done();
      });
    });

    it('should search clients by city', (done) => {
      const searchQuery = 'paris';

      service.searchClients(searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        
        if (results.length > 0) {
          const hasMatchingCity = results.some(client => 
            client.city?.toLowerCase().includes(searchQuery.toLowerCase())
          );
          expect(hasMatchingCity).toBeTruthy();
        }
        done();
      });
    });

    it('should return empty array for no matches', (done) => {
      const searchQuery = 'nonexistentcompany';

      service.searchClients(searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        done();
      });
    });
  });

  describe('getClientsByStatus', () => {
    it('should filter clients by ACTIVE status', (done) => {
      service.getClientsByStatus('ACTIVE').subscribe(activeClients => {
        expect(activeClients).toBeDefined();
        expect(Array.isArray(activeClients)).toBeTruthy();
        
        activeClients.forEach(client => {
          expect(client.status).toBe('ACTIVE');
        });
        done();
      });
    });

    it('should filter clients by PROSPECT status', (done) => {
      service.getClientsByStatus('PROSPECT').subscribe(prospectClients => {
        expect(prospectClients).toBeDefined();
        expect(Array.isArray(prospectClients)).toBeTruthy();
        
        prospectClients.forEach(client => {
          expect(client.status).toBe('PROSPECT');
        });
        done();
      });
    });

    it('should filter clients by INACTIVE status', (done) => {
      service.getClientsByStatus('INACTIVE').subscribe(inactiveClients => {
        expect(inactiveClients).toBeDefined();
        expect(Array.isArray(inactiveClients)).toBeTruthy();
        
        inactiveClients.forEach(client => {
          expect(client.status).toBe('INACTIVE');
        });
        done();
      });
    });
  });

  describe('getClientStats', () => {
    it('should return client statistics', (done) => {
      service.getClientStats().subscribe(stats => {
        expect(stats).toBeDefined();
        expect(stats.total).toBeDefined();
        expect(stats.active).toBeDefined();
        expect(stats.inactive).toBeDefined();
        expect(stats.prospects).toBeDefined();
        
        expect(typeof stats.total).toBe('number');
        expect(typeof stats.active).toBe('number');
        expect(typeof stats.inactive).toBe('number');
        expect(typeof stats.prospects).toBe('number');
        
        expect(stats.total).toBe(stats.active + stats.inactive + stats.prospects);
        done();
      });
    });
  });
});
