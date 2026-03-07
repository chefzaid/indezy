import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ContactService } from './contact.service';
import { ContactDto } from '../../models';
import { environment } from '../../../environments/environment';

describe('ContactService', () => {
  let service: ContactService;
  let httpMock: HttpTestingController;

  const mockContact: ContactDto = {
    id: 1,
    firstName: 'Marie',
    lastName: 'Dubois',
    email: 'marie.dubois@techcorp.fr',
    phone: '+33 1 23 45 67 89',
    position: 'Directrice RH',
    clientId: 1,
    clientName: 'TechCorp Solutions',
    freelanceId: 1,
    fullName: 'Marie Dubois',
    notes: 'Contact principal pour les recrutements développeurs',
    status: 'ACTIVE',
    createdAt: new Date('2024-01-15'),
    updatedAt: new Date('2024-01-15')
  };

  const mockContacts: ContactDto[] = [
    mockContact,
    {
      ...mockContact,
      id: 2,
      firstName: 'Pierre',
      lastName: 'Martin',
      email: 'p.martin@innovate.com',
      fullName: 'Pierre Martin',
      clientId: 2,
      clientName: 'Innovate Digital'
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ContactService]
    });

    service = TestBed.inject(ContactService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should get all contacts', () => {
    service.getAll().subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts`);
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });

  it('should get contact by id', () => {
    const contactId = 1;

    service.getById(contactId).subscribe(contact => {
      expect(contact).toEqual(mockContact);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/${contactId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockContact);
  });

  it('should get contacts (legacy method)', () => {
    service.getContacts().subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts`);
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });

  it('should get contact by id (legacy method)', () => {
    const contactId = 1;

    service.getContact(contactId).subscribe(contact => {
      expect(contact).toEqual(mockContact);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/${contactId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockContact);
  });

  it('should create contact', () => {
    const newContact: ContactDto = {
      firstName: 'New',
      lastName: 'Contact',
      email: 'new@example.com',
      phone: '+33 1 00 00 00 00',
      position: 'Developer',
      clientId: 1,
      freelanceId: 1
    };

    service.create(newContact).subscribe(contact => {
      expect(contact).toEqual(mockContact);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(newContact);
    req.flush(mockContact);
  });

  it('should create contact (legacy method)', () => {
    const newContact: ContactDto = {
      firstName: 'New',
      lastName: 'Contact',
      email: 'new@example.com',
      phone: '+33 1 00 00 00 00',
      position: 'Developer',
      clientId: 1,
      freelanceId: 1
    };

    service.createContact(newContact).subscribe(contact => {
      expect(contact).toEqual(mockContact);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(newContact);
    req.flush(mockContact);
  });

  it('should update contact', () => {
    const contactId = 1;
    const updatedContact: ContactDto = {
      ...mockContact,
      firstName: 'Updated'
    };

    service.update(contactId, updatedContact).subscribe(contact => {
      expect(contact).toEqual(updatedContact);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/${contactId}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updatedContact);
    req.flush(updatedContact);
  });

  it('should update contact (legacy method)', () => {
    const contactId = 1;
    const updateData = { firstName: 'Updated' };

    service.updateContact(contactId, updateData).subscribe(contact => {
      expect(contact).toEqual(mockContact);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/${contactId}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updateData);
    req.flush(mockContact);
  });

  it('should delete contact', () => {
    const contactId = 1;

    service.delete(contactId).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/${contactId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should delete contact (legacy method)', () => {
    const contactId = 1;

    service.deleteContact(contactId).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/${contactId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should get contacts by freelance id', () => {
    const freelanceId = 1;

    service.getByFreelanceId(freelanceId).subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/by-freelance/${freelanceId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });

  it('should get contacts by client id', () => {
    const clientId = 1;

    service.getByClientId(clientId).subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/by-client/${clientId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });

  it('should search contacts by name', () => {
    const freelanceId = 1;
    const name = 'Marie';

    service.searchByName(freelanceId, name).subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(req =>
      req.url === `${environment.apiUrl}/contacts/by-freelance/${freelanceId}/search/name` &&
      req.params.get('name') === name
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });

  it('should search contacts by email', () => {
    const freelanceId = 1;
    const email = 'marie@example.com';

    service.searchByEmail(freelanceId, email).subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(req =>
      req.url === `${environment.apiUrl}/contacts/by-freelance/${freelanceId}/search/email` &&
      req.params.get('email') === email
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });

  it('should get contacts by client (legacy method)', () => {
    const clientId = 1;

    service.getContactsByClient(clientId).subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/by-client/${clientId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });

  it('should filter contacts by client id', () => {
    const filters = { clientId: 1 };

    service.filterContacts(filters).subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/contacts/by-client/${filters.clientId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });

  it('should filter contacts by status', () => {
    const filters = { status: 'ACTIVE' };

    service.filterContacts(filters).subscribe(contacts => {
      expect(contacts).toEqual(mockContacts);
    });

    const req = httpMock.expectOne(req =>
      req.url === `${environment.apiUrl}/contacts` &&
      req.params.get('status') === 'ACTIVE'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockContacts);
  });
});
