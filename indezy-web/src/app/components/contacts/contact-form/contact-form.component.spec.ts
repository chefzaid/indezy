import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { ClientDto, ContactDto } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientService } from '../../../services/client/client.service';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ContactFormComponent } from './contact-form.component';

describe('ContactFormComponent', () => {
  let contacts: jasmine.SpyObj<ContactService>;
  let clients: jasmine.SpyObj<ClientService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let navigate: jasmine.Spy;

  const loaded: ContactDto = { id: 3, firstName: 'Alice', lastName: 'Martin', email: 'alice@acme.fr', phone: '0601',
    clientId: 1, clientName: 'Acme', freelanceId: 42, notes: 'CTO' };

  interface Options {
    managers?: Observable<ContactDto[]>;
    params?: Record<string, number>;
    query?: Record<string, string>;
    clientList?: Observable<ClientDto[]>;
    contact?: Observable<ContactDto | null>;
  }

  function create(options: Options = {}): ComponentFixture<ContactFormComponent> {
    contacts = jasmine.createSpyObj('ContactService', ['getById', 'create', 'update', 'getForCurrentFreelance']);
    contacts.getForCurrentFreelance.and.returnValue(options.managers ?? of([]));
    clients = jasmine.createSpyObj('ClientService', ['getForCurrentFreelance']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    clients.getForCurrentFreelance.and.returnValue(options.clientList ?? of([
      { id: 2, companyName: 'Zenika', city: 'Paris', isFinal: false },
      { id: 1, companyName: 'Acme', city: 'Lyon', isFinal: true }
    ]));
    contacts.getById.and.returnValue((options.contact ?? of(loaded)) as Observable<ContactDto>);
    TestBed.configureTestingModule({
      imports: [ContactFormComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: {
          params: of(options.params ?? {}),
          snapshot: { queryParamMap: convertToParamMap(options.query ?? {}) }
        } },
        { provide: ContactService, useValue: contacts },
        { provide: ClientService, useValue: clients },
        { provide: AuthService, useValue: { getUser: () => ({ id: 7 }) } },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(ContactFormComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ContactFormComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function fill(component: ContactFormComponent, values: Record<string, unknown> = {}): void {
    component.contactForm.patchValue({ firstName: ' Bob ', lastName: ' Durand ', email: '', phone: '', notes: '  ', clientId: 1,
      ...values });
  }

  describe('creating a contact', () => {
    it('offers the clients by name and creates a trimmed contact', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      contacts.create.and.returnValue(of({ ...loaded, id: 12 }));

      expect(component.clients.map(c => c.companyName)).toEqual(['Acme', 'Zenika']);
      expect(component.pageTitle).toBe('contacts.newContact');
      expect(component.submitButtonText).toBe('common.create');
      expect(text(fixture)).toContain('contacts.newSubtitle');

      fill(component, { email: 'bob@acme.fr' });
      (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();

      expect(contacts.create).toHaveBeenCalledWith({
        firstName: 'Bob', lastName: 'Durand', email: 'bob@acme.fr', phone: null, notes: null, legalBasis: 'LEGITIMATE_INTEREST',
        clientId: 1, managerId: null, freelanceId: 7
      });
      expect(notifications.success).toHaveBeenCalledWith('contacts.createSuccess');
      expect(navigate).toHaveBeenCalledWith(['/contacts', 12]);
    });

    it('returns to the list when the saved contact has no id', () => {
      const component = create().componentInstance;
      contacts.create.and.returnValue(of(undefined as unknown as ContactDto));
      fill(component);

      component.onSubmit();

      expect(navigate).toHaveBeenCalledWith(['/contacts']);
    });

    it('shows the errors of an incomplete form instead of saving', () => {
      const component = create().componentInstance;
      component.contactForm.patchValue({ firstName: 'B', email: 'nope' });

      component.onSubmit();

      expect(contacts.create).not.toHaveBeenCalled();
      expect(component.getFieldError('firstName')).toBe('errors.minLength');
      expect(component.getFieldError('lastName')).toBe('errors.fieldRequired');
      expect(component.getFieldError('email')).toBe('errors.invalidEmail');
      expect(component.getFieldError('phone')).toBe('');
    });

    it('does not save twice while a save is in progress', () => {
      const component = create().componentInstance;
      fill(component);
      component.isSubmitting = true;

      component.onSubmit();

      expect(contacts.create).not.toHaveBeenCalled();
    });

    it('reports a failed creation and lets the user retry', () => {
      spyOn(console, 'error');
      const component = create().componentInstance;
      contacts.create.and.returnValue(throwError(() => new Error('down')));
      fill(component);

      component.onSubmit();

      expect(notifications.error).toHaveBeenCalledWith('errors.creatingContact');
      expect(component.isSubmitting).toBeFalse();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('preselects the client passed in the address', () => {
      const component = create({ query: { clientId: '2' } }).componentInstance;

      expect(component.contactForm.value.clientId).toBe(2);
    });

    it('hints at creating a client when there is none', () => {
      const fixture = create({ clientList: of([]) });

      expect(text(fixture)).toContain('contacts.noClientsHint');
    });

    it('reports clients that cannot be loaded', () => {
      spyOn(console, 'error');
      create({ clientList: throwError(() => new Error('down')) });

      expect(notifications.error).toHaveBeenCalledWith('errors.loadingClients');
    });

    it('cancels back to the contact list', () => {
      const fixture = create();

      (fixture.nativeElement.querySelector('button[type="button"]') as HTMLButtonElement).click();

      expect(navigate).toHaveBeenCalledWith(['/contacts']);
    });
  });

  describe('from a client page', () => {
    it('assigns the client and returns to its contacts tab', () => {
      const component = create({ params: { id: 5 }, query: { clientId: '2' } }).componentInstance;
      contacts.create.and.returnValue(of({ ...loaded, id: 12 }));

      expect(component.parentClientId).toBe(5);
      expect(component.contactForm.value.clientId).toBe(5);

      fill(component, { clientId: 5 });
      component.onSubmit();
      expect(navigate).toHaveBeenCalledWith(['/clients', 5], { queryParams: { tab: 'contacts' } });

      navigate.calls.reset();
      component.onCancel();
      expect(navigate).toHaveBeenCalledWith(['/clients', 5], { queryParams: { tab: 'contacts' } });
    });
  });

  describe('choosing whom the contact reports to', () => {
    const nadia: ContactDto = { id: 5, firstName: 'Nadia', lastName: 'Kaci', clientId: 2, clientName: 'Esn' };
    const self: ContactDto = { id: 3, firstName: 'Alice', clientId: 1, clientName: 'Acme' };
    const adam: ContactDto = { id: 6, firstName: 'Adam', clientId: 1, clientName: 'Acme' };

    it('offers the other contacts by name, saves the choice and explains a reporting loop', () => {
      const fixture = create({ params: { contactId: 3 }, managers: of([nadia, self, adam]) });
      const component = fixture.componentInstance;
      contacts.update.and.returnValue(throwError(() => ({ status: 400, error: { validationErrors: { managerId: 'loop' } } })));

      expect(component.managerOptions.map(contact => component.nameOf(contact))).toEqual(['Adam', 'Nadia Kaci']);
      component.contactForm.patchValue({ managerId: 5 });
      component.onSubmit();

      expect(contacts.update).toHaveBeenCalledWith(3, jasmine.objectContaining({ managerId: 5 }));
      expect(notifications.error).toHaveBeenCalledWith('contacts.network.managerLoop');
    });

    it('works without the other contacts when they cannot be loaded', () => {
      const component = create({ managers: throwError(() => new Error('down')) }).componentInstance;

      expect(component.managerOptions).toEqual([]);
    });
  });

  describe('editing a contact', () => {
    it('loads the contact and saves it with its original owner', () => {
      const fixture = create({ params: { contactId: 3 }, query: { clientId: '2' } });
      const component = fixture.componentInstance;
      contacts.update.and.returnValue(of(loaded));

      expect(contacts.getById).toHaveBeenCalledWith(3);
      expect(component.isEditMode).toBeTrue();
      expect(component.isLoading).toBeFalse();
      expect(component.contactForm.value).toEqual({
        firstName: 'Alice', lastName: 'Martin', email: 'alice@acme.fr', phone: '0601', clientId: 1, managerId: null, notes: 'CTO',
        legalBasis: 'LEGITIMATE_INTEREST'
      });
      expect(component.pageTitle).toBe('contacts.editContact');
      expect(component.submitButtonText).toBe('common.edit');
      expect(text(fixture)).toContain('contacts.editSubtitle');

      // An emptied field is sent as null, so it is cleared.
      component.contactForm.patchValue({ lastName: ' Martin-Durand ', phone: ' 0700 ', notes: ' ', legalBasis: 'CONSENT' });
      component.onSubmit();

      expect(contacts.update).toHaveBeenCalledWith(3, {
        ...loaded, lastName: 'Martin-Durand', phone: '0700', notes: null, legalBasis: 'CONSENT', managerId: null, freelanceId: 42
      });
      expect(notifications.success).toHaveBeenCalledWith('contacts.updateSuccess');
      expect(navigate).toHaveBeenCalledWith(['/contacts', 3]);
    });

    it('falls back to the edited contact when the save returns nothing', () => {
      const component = create({ params: { contactId: 3 } }).componentInstance;
      contacts.update.and.returnValue(of(undefined as unknown as ContactDto));

      component.onSubmit();

      expect(navigate).toHaveBeenCalledWith(['/contacts', 3]);
    });

    it('reports a failed update', () => {
      spyOn(console, 'error');
      const component = create({ params: { contactId: 3 } }).componentInstance;
      contacts.update.and.returnValue(throwError(() => new Error('down')));

      component.onSubmit();

      expect(notifications.error).toHaveBeenCalledWith('errors.updatingContact');
      expect(component.isSubmitting).toBeFalse();
    });

    it('cancels back to the contact', () => {
      const component = create({ params: { contactId: 3 } }).componentInstance;

      component.onCancel();

      expect(navigate).toHaveBeenCalledWith(['/contacts', 3]);
    });

    it('returns to the list when the contact is missing', () => {
      create({ params: { contactId: 3 }, contact: of(null) });

      expect(notifications.error).toHaveBeenCalledWith('errors.contactNotFound');
      expect(navigate).toHaveBeenCalledWith(['/contacts']);
    });

    it('returns to the list when the contact cannot be loaded', () => {
      spyOn(console, 'error');
      const component = create({ params: { contactId: 3 }, contact: throwError(() => ({ status: 404 })) }).componentInstance;

      expect(notifications.error).toHaveBeenCalledWith('errors.contactNotFound');
      expect(navigate).toHaveBeenCalledWith(['/contacts']);
      expect(component.isLoading).toBeFalse();

      TestBed.resetTestingModule();
      create({ params: { contactId: 3 }, contact: throwError(() => ({ status: 500 })) });
      expect(notifications.error).toHaveBeenCalledWith('errors.loadingContact');
    });
  });
});
