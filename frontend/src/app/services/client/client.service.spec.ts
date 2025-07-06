import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ClientService } from './client.service';
import { ClientDto, CreateClientDto, UpdateClientDto } from '../../models';

describe('ClientService', () => {
  let service: ClientService;
  let httpMock: HttpTestingController;
  const API_URL = 'http://localhost:8080/api/clients';

  const mockClient: ClientDto = {
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

  const mockClients: ClientDto[] = [
    mockClient,
    {
      ...mockClient,
      id: 2,
      companyName: 'StartupBoost',
      name: 'StartupBoost',
      status: 'PROSPECT',
      isFinal: false
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ClientService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(ClientService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getClients', () => {
    it('should return observable of clients', () => {
      service.getClients().subscribe(clients => {
        expect(clients).toBeDefined();
        expect(Array.isArray(clients)).toBeTruthy();
        expect(clients.length).toBe(2);
        expect(clients).toEqual(mockClients);
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('GET');
      req.flush(mockClients);
    });

    it('should return clients with expected properties', () => {
      service.getClients().subscribe(clients => {
        const client = clients[0];
        expect(client.id).toBeDefined();
        expect(client.companyName).toBeDefined();
        expect(client.city).toBeDefined();
        expect(client.status).toBeDefined();
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('GET');
      req.flush(mockClients);
    });
  });

  describe('getClient', () => {
    it('should return specific client by id', () => {
      const clientId = 1;

      service.getClient(clientId).subscribe(client => {
        expect(client).toBeDefined();
        expect(client?.id).toBe(clientId);
      });

      const req = httpMock.expectOne(`${API_URL}/${clientId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockClient);
    });

    it('should return undefined for non-existent client', () => {
      const nonExistentId = 999;

      service.getClient(nonExistentId).subscribe(client => {
        expect(client).toBeNull();
      });

      const req = httpMock.expectOne(`${API_URL}/${nonExistentId}`);
      expect(req.request.method).toBe('GET');
      req.flush(null);
    });

    it('should simulate API delay', () => {
      const clientId = 1;

      service.getClient(clientId).subscribe(client => {
        expect(client).toBeDefined();
        expect(client?.id).toBe(clientId);
      });

      const req = httpMock.expectOne(`${API_URL}/${clientId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockClient);
    });
  });

  describe('createClient', () => {
    it('should create a new client', () => {
      const newClientData: CreateClientDto = {
        companyName: 'New Tech Company',
        city: 'Lyon',
        domain: 'AI/ML',
        isFinal: false,
        status: 'PROSPECT'
      };

      const expectedClient = { ...mockClient, ...newClientData, id: 3 };

      service.createClient(newClientData).subscribe(createdClient => {
        expect(createdClient).toBeDefined();
        expect(createdClient.companyName).toBe(newClientData.companyName);
        expect(createdClient.city).toBe(newClientData.city);
        expect(createdClient.id).toBeDefined();
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newClientData);
      req.flush(expectedClient);
    });

    it('should assign incremental id to new client', () => {
      const newClientData: CreateClientDto = {
        companyName: 'Another Company',
        city: 'Marseille',
        isFinal: true,
        status: 'ACTIVE'
      };

      const expectedClient = { ...mockClient, ...newClientData, id: 4 };

      service.createClient(newClientData).subscribe(createdClient => {
        expect(createdClient.id).toBeGreaterThan(0);
        expect(createdClient.id).toBe(4);
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('POST');
      req.flush(expectedClient);
    });
  });

  describe('updateClient', () => {
    it('should update existing client', () => {
      const updateData: UpdateClientDto = {
        id: 1,
        companyName: 'Updated Company Name',
        notes: 'Updated notes'
      };

      const updatedClient = { ...mockClient, ...updateData };

      service.updateClient(updateData).subscribe(result => {
        expect(result).toBeDefined();
        expect(result.companyName).toBe('Updated Company Name');
        expect(result.notes).toBe('Updated notes');
      });

      const req = httpMock.expectOne(`${API_URL}/${updateData.id}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateData);
      req.flush(updatedClient);
    });

    it('should throw error for non-existent client update', () => {
      const updateData: UpdateClientDto = {
        id: 999,
        companyName: 'Non-existent Company'
      };

      service.updateClient(updateData).subscribe({
        next: () => fail('Should have failed'),
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${API_URL}/${updateData.id}`);
      expect(req.request.method).toBe('PUT');
      req.flush('Client not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('deleteClient', () => {
    it('should delete existing client', () => {
      const clientId = 1;

      service.deleteClient(clientId).subscribe(result => {
        expect(result).toBeNull(); // deleteClient returns Observable<void> but HTTP mock returns null
      });

      const req = httpMock.expectOne(`${API_URL}/${clientId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should throw error for non-existent client deletion', () => {
      const nonExistentId = 999;

      service.deleteClient(nonExistentId).subscribe({
        next: () => fail('Should have failed'),
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${API_URL}/${nonExistentId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush('Client not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('searchClients', () => {
    it('should search clients by company name', () => {
      const freelanceId = 1;
      const searchQuery = 'tech';
      const filteredClients = mockClients.filter(client =>
        client.companyName?.toLowerCase().includes(searchQuery.toLowerCase())
      );

      service.searchClients(freelanceId, searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        expect(results).toEqual(filteredClients);
      });

      const req = httpMock.expectOne(`${API_URL}/search?freelanceId=${freelanceId}&query=${searchQuery}`);
      expect(req.request.method).toBe('GET');
      req.flush(filteredClients);
    });

    it('should search clients by city', () => {
      const freelanceId = 1;
      const searchQuery = 'paris';
      const filteredClients = mockClients.filter(client =>
        client.city?.toLowerCase().includes(searchQuery.toLowerCase())
      );

      service.searchClients(freelanceId, searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        expect(results).toEqual(filteredClients);
      });

      const req = httpMock.expectOne(`${API_URL}/search?freelanceId=${freelanceId}&query=${searchQuery}`);
      expect(req.request.method).toBe('GET');
      req.flush(filteredClients);
    });

    it('should return empty array for no matches', () => {
      const freelanceId = 1;
      const searchQuery = 'nonexistentcompany';

      service.searchClients(freelanceId, searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        expect(results.length).toBe(0);
      });

      const req = httpMock.expectOne(`${API_URL}/search?freelanceId=${freelanceId}&query=${searchQuery}`);
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('getClientsByStatus', () => {
    it('should filter clients by ACTIVE status', () => {
      const freelanceId = 1;
      const activeClients = mockClients.filter(client => client.status === 'ACTIVE');

      service.getClientsByStatus(freelanceId, 'ACTIVE').subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        expect(results).toEqual(activeClients);
        results.forEach(client => {
          expect(client.status).toBe('ACTIVE');
        });
      });

      const req = httpMock.expectOne(`${API_URL}/by-status?freelanceId=${freelanceId}&status=ACTIVE`);
      expect(req.request.method).toBe('GET');
      req.flush(activeClients);
    });

    it('should filter clients by PROSPECT status', () => {
      const freelanceId = 1;
      const prospectClients = mockClients.filter(client => client.status === 'PROSPECT');

      service.getClientsByStatus(freelanceId, 'PROSPECT').subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        expect(results).toEqual(prospectClients);
        results.forEach(client => {
          expect(client.status).toBe('PROSPECT');
        });
      });

      const req = httpMock.expectOne(`${API_URL}/by-status?freelanceId=${freelanceId}&status=PROSPECT`);
      expect(req.request.method).toBe('GET');
      req.flush(prospectClients);
    });

    it('should filter clients by INACTIVE status', () => {
      const freelanceId = 1;
      const inactiveClients = mockClients.filter(client => client.status === 'INACTIVE');

      service.getClientsByStatus(freelanceId, 'INACTIVE').subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        expect(results).toEqual(inactiveClients);
        results.forEach(client => {
          expect(client.status).toBe('INACTIVE');
        });
      });

      const req = httpMock.expectOne(`${API_URL}/by-status?freelanceId=${freelanceId}&status=INACTIVE`);
      expect(req.request.method).toBe('GET');
      req.flush(inactiveClients);
    });
  });

  describe('getClientStats', () => {
    it('should return client statistics', () => {
      const freelanceId = 1;
      const mockStats = {
        total: 2,
        active: 1,
        inactive: 0,
        prospects: 1
      };

      service.getClientStats(freelanceId).subscribe(stats => {
        expect(stats).toBeDefined();
        expect(stats.total).toBe(2);
        expect(stats.active).toBe(1);
        expect(stats.inactive).toBe(0);
        expect(stats.prospects).toBe(1);

        expect(typeof stats.total).toBe('number');
        expect(typeof stats.active).toBe('number');
        expect(typeof stats.inactive).toBe('number');
        expect(typeof stats.prospects).toBe('number');

        expect(stats.total).toBe(stats.active + stats.inactive + stats.prospects);
      });

      const req = httpMock.expectOne(`${API_URL}/stats/${freelanceId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockStats);
    });
  });
});
