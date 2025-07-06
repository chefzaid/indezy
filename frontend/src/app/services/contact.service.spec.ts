import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ContactService, ContactDto } from './contact.service';

describe('ContactService', () => {
  let service: ContactService;

  const mockContact: ContactDto = {
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
  };

  // const mockContacts: ContactDto[] = [
    mockContact,
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
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ContactService]
    });

    service = TestBed.inject(ContactService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getContacts', () => {
    it('should return observable of contacts', (done) => {
      service.getContacts().subscribe(contacts => {
        expect(contacts).toBeDefined();
        expect(Array.isArray(contacts)).toBeTruthy();
        expect(contacts.length).toBeGreaterThan(0);
        done();
      });
    });

    it('should return contacts with expected properties', (done) => {
      service.getContacts().subscribe(contacts => {
        const contact = contacts[0];
        expect(contact.id).toBeDefined();
        expect(contact.firstName).toBeDefined();
        expect(contact.lastName).toBeDefined();
        expect(contact.email).toBeDefined();
        expect(contact.phone).toBeDefined();
        expect(contact.position).toBeDefined();
        expect(contact.clientId).toBeDefined();
        expect(contact.clientName).toBeDefined();
        expect(contact.status).toBeDefined();
        done();
      });
    });

    it('should simulate API delay', (done) => {
      const startTime = Date.now();
      
      service.getContacts().subscribe(() => {
        const endTime = Date.now();
        const duration = endTime - startTime;
        expect(duration).toBeGreaterThanOrEqual(450); // Should have ~500ms delay
        done();
      });
    });
  });

  describe('getContact', () => {
    it('should return specific contact by id', (done) => {
      const contactId = 1;
      
      service.getContact(contactId).subscribe(contact => {
        expect(contact).toBeDefined();
        expect(contact?.id).toBe(contactId);
        expect(contact?.firstName).toBe('Marie');
        expect(contact?.lastName).toBe('Dubois');
        done();
      });
    });

    it('should return undefined for non-existent contact', (done) => {
      const nonExistentId = 999;
      
      service.getContact(nonExistentId).subscribe(contact => {
        expect(contact).toBeUndefined();
        done();
      });
    });
  });

  describe('createContact', () => {
    it('should create a new contact', (done) => {
      const newContactData = {
        firstName: 'Jean',
        lastName: 'Dupont',
        email: 'jean.dupont@example.com',
        phone: '+33 1 11 22 33 44',
        position: 'Manager',
        clientId: 1,
        clientName: 'Test Client',
        status: 'ACTIVE' as const
      };

      service.createContact(newContactData).subscribe(createdContact => {
        expect(createdContact).toBeDefined();
        expect(createdContact.firstName).toBe(newContactData.firstName);
        expect(createdContact.lastName).toBe(newContactData.lastName);
        expect(createdContact.email).toBe(newContactData.email);
        expect(createdContact.id).toBeDefined();
        expect(createdContact.createdAt).toBeDefined();
        expect(createdContact.updatedAt).toBeDefined();
        done();
      });
    });

    it('should assign incremental id to new contact', (done) => {
      const newContactData = {
        firstName: 'Test',
        lastName: 'User',
        email: 'test@example.com',
        phone: '+33 1 00 00 00 00',
        position: 'Tester',
        clientId: 2,
        clientName: 'Test Client 2',
        status: 'ACTIVE' as const
      };

      service.createContact(newContactData).subscribe(createdContact => {
        expect(createdContact.id).toBeGreaterThan(0);
        done();
      });
    });
  });

  describe('updateContact', () => {
    it('should update existing contact', (done) => {
      const contactId = 1;
      const updateData = {
        firstName: 'Marie-Claire',
        position: 'Directrice RH Senior',
        notes: 'Updated notes'
      };

      service.updateContact(contactId, updateData).subscribe(updatedContact => {
        expect(updatedContact).toBeDefined();
        expect(updatedContact?.firstName).toBe(updateData.firstName);
        expect(updatedContact?.position).toBe(updateData.position);
        expect(updatedContact?.notes).toBe(updateData.notes);
        expect(updatedContact?.updatedAt).toBeDefined();
        done();
      });
    });

    it('should throw error for non-existent contact update', () => {
      const nonExistentId = 999;
      const updateData = { firstName: 'Non-existent' };

      expect(() => {
        service.updateContact(nonExistentId, updateData);
      }).toThrowError('Contact not found');
    });
  });

  describe('deleteContact', () => {
    it('should delete existing contact', (done) => {
      const contactId = 1;

      service.deleteContact(contactId).subscribe(() => {
        // Verify the contact was deleted by trying to get it
        service.getContact(contactId).subscribe(contact => {
          expect(contact).toBeUndefined();
          done();
        });
      });
    });

    it('should complete successfully for non-existent contact deletion', (done) => {
      const nonExistentId = 999;

      service.deleteContact(nonExistentId).subscribe({
        next: (result) => {
          // Delete operation completed (no error for non-existent contact)
          expect(result).toBeUndefined(); // Service returns void for delete operations
          done();
        }
      });
    });
  });

  describe('searchContacts', () => {
    it('should search contacts by first name', (done) => {
      const searchQuery = 'marie';

      service.searchContacts(searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        expect(Array.isArray(results)).toBeTruthy();
        
        if (results.length > 0) {
          const hasMatchingName = results.some(contact => 
            contact.firstName.toLowerCase().includes(searchQuery.toLowerCase())
          );
          expect(hasMatchingName).toBeTruthy();
        }
        done();
      });
    });

    it('should search contacts by email', (done) => {
      const searchQuery = 'techcorp';

      service.searchContacts(searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        
        if (results.length > 0) {
          const hasMatchingEmail = results.some(contact => 
            contact.email.toLowerCase().includes(searchQuery.toLowerCase())
          );
          expect(hasMatchingEmail).toBeTruthy();
        }
        done();
      });
    });

    it('should search contacts by position', (done) => {
      const searchQuery = 'directrice';

      service.searchContacts(searchQuery).subscribe(results => {
        expect(results).toBeDefined();
        
        if (results.length > 0) {
          const hasMatchingPosition = results.some(contact => 
            contact.position.toLowerCase().includes(searchQuery.toLowerCase())
          );
          expect(hasMatchingPosition).toBeTruthy();
        }
        done();
      });
    });
  });

  describe('getContactsByClient', () => {
    it('should return contacts for specific client', (done) => {
      const clientId = 1;

      service.getContactsByClient(clientId).subscribe(contacts => {
        expect(contacts).toBeDefined();
        expect(Array.isArray(contacts)).toBeTruthy();
        
        contacts.forEach(contact => {
          expect(contact.clientId).toBe(clientId);
        });
        done();
      });
    });

    it('should return empty array for client with no contacts', (done) => {
      const clientId = 999;

      service.getContactsByClient(clientId).subscribe(contacts => {
        expect(contacts).toBeDefined();
        expect(Array.isArray(contacts)).toBeTruthy();
        expect(contacts.length).toBe(0);
        done();
      });
    });
  });

  describe('filterContacts', () => {
    it('should filter contacts by status', (done) => {
      const filters = { status: 'ACTIVE' };

      service.filterContacts(filters).subscribe(filteredContacts => {
        expect(filteredContacts).toBeDefined();
        expect(Array.isArray(filteredContacts)).toBeTruthy();
        
        filteredContacts.forEach(contact => {
          expect(contact.status).toBe('ACTIVE');
        });
        done();
      });
    });

    it('should filter contacts by client id', (done) => {
      const filters = { clientId: 1 };

      service.filterContacts(filters).subscribe(filteredContacts => {
        expect(filteredContacts).toBeDefined();
        expect(Array.isArray(filteredContacts)).toBeTruthy();
        
        filteredContacts.forEach(contact => {
          expect(contact.clientId).toBe(1);
        });
        done();
      });
    });

    it('should filter contacts by multiple criteria', (done) => {
      const filters = { status: 'ACTIVE', clientId: 1 };

      service.filterContacts(filters).subscribe(filteredContacts => {
        expect(filteredContacts).toBeDefined();
        expect(Array.isArray(filteredContacts)).toBeTruthy();
        
        filteredContacts.forEach(contact => {
          expect(contact.status).toBe('ACTIVE');
          expect(contact.clientId).toBe(1);
        });
        done();
      });
    });

    it('should return all contacts when no filters applied', (done) => {
      const filters = {};

      service.filterContacts(filters).subscribe(filteredContacts => {
        expect(filteredContacts).toBeDefined();
        expect(Array.isArray(filteredContacts)).toBeTruthy();
        expect(filteredContacts.length).toBeGreaterThan(0);
        done();
      });
    });
  });
});
