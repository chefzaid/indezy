import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { ClientDto, ContactDto } from '../../../models';
import { ClientService } from '../../../services/client/client.service';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ContactListComponent } from './contact-list.component';

describe('ContactListComponent', () => {
  let contacts: jasmine.SpyObj<ContactService>;
  let clients: jasmine.SpyObj<ClientService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let navigate: jasmine.Spy;

  const zoe: ContactDto = { id: 1, firstName: 'Zoé', lastName: 'Leroy', clientId: 2, clientName: 'Zenika', email: 'zoe@zenika.fr',
    phone: '0601' };
  const alice: ContactDto = { id: 2, firstName: 'Alice', lastName: 'Martin', clientId: 1, clientName: 'Acme' };
  const bob: ContactDto = { id: 3, firstName: 'Bob', clientId: 1, clientName: 'Acme' };

  function create(contactList: Observable<ContactDto[]> = of([zoe, alice, bob]),
                  clientList: Observable<ClientDto[]> = of([
                    { id: 2, companyName: 'Zenika', city: 'Paris', isFinal: false },
                    { id: 1, companyName: 'Acme', city: 'Lyon', isFinal: true }
                  ])): ComponentFixture<ContactListComponent> {
    contacts = jasmine.createSpyObj('ContactService', ['getForCurrentFreelance', 'delete', 'deleteAll']);
    clients = jasmine.createSpyObj('ClientService', ['getForCurrentFreelance']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    contacts.getForCurrentFreelance.and.returnValue(contactList);
    clients.getForCurrentFreelance.and.returnValue(clientList);
    TestBed.configureTestingModule({
      imports: [ContactListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ContactService, useValue: contacts },
        { provide: ClientService, useValue: clients },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(ContactListComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ContactListComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function names(component: ContactListComponent): string[] {
    return component.filteredContacts.map(contact => component.getFullName(contact));
  }

  it('says how long ago each contact was last contacted', () => {
    const day = new Date();
    day.setDate(day.getDate() - 14);
    const twoWeeksAgo = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`;
    const fixture = create(of([{ ...zoe, lastContactedOn: twoWeeksAgo }, alice]));

    const cells = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('td.mat-column-lastContact'))
      .map(cell => cell.textContent!.trim());
    expect(cells).toEqual(['—', '2 weeks ago']);
    expect(fixture.componentInstance.lastContact(alice)).toBe('');
  });

  it('lists the contacts by name with their links and client', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    expect(names(component)).toEqual(['Alice Martin', 'Bob', 'Zoé Leroy']);
    expect(component.clients.map(c => c.companyName)).toEqual(['Acme', 'Zenika']);
    expect(component.isLoading).toBeFalse();
    const page = text(fixture);
    expect(page).toContain('Zoé Leroy');
    expect(page).toContain('Zenika');
    expect(fixture.nativeElement.querySelector('a.email-link').getAttribute('href')).toBe('mailto:zoe@zenika.fr');
    expect(fixture.nativeElement.querySelector('a.phone-link').getAttribute('href')).toBe('tel:0601');
  });

  it('reports contacts that cannot be loaded', () => {
    spyOn(console, 'error');
    const component = create(throwError(() => new Error('down'))).componentInstance;

    expect(notifications.error).toHaveBeenCalledWith('errors.loadingContacts');
    expect(component.isLoading).toBeFalse();
  });

  it('keeps working without the client filter when clients cannot be loaded', () => {
    const consoleError = spyOn(console, 'error');
    const component = create(undefined, throwError(() => new Error('down'))).componentInstance;

    expect(component.clients).toEqual([]);
    expect(consoleError).toHaveBeenCalled();
    expect(notifications.error).not.toHaveBeenCalled();
  });

  it('invites to add a first contact when there is none', () => {
    const fixture = create(of([]));

    expect(text(fixture)).toContain('contacts.addFirstContact');
    (fixture.nativeElement.querySelector('.no-data button') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenCalledWith(['/contacts/new'], {});
  });

  it('searches names, emails and clients after a short pause', fakeAsync(() => {
    const fixture = create();
    const component = fixture.componentInstance;

    component.onSearchChange(' ZENIKA ');
    expect(names(component).length).toBe(3);
    tick(200);
    expect(names(component)).toEqual(['Zoé Leroy']);
    expect(component.hasActiveFilters()).toBeTrue();

    component.onSearchChange('nobody');
    tick(200);
    expect(text(fixture)).toContain('contacts.noMatchingContacts');

    component.clearFilters();
    expect(names(component).length).toBe(3);
    expect(component.hasActiveFilters()).toBeFalse();
  }));

  it('filters by client and creates a contact for it', () => {
    const component = create().componentInstance;

    component.selectedClient = 1;
    component.onClientFilterChange();

    expect(names(component)).toEqual(['Alice Martin', 'Bob']);
    expect(component.hasActiveFilters()).toBeTrue();
    component.onCreate();
    expect(navigate).toHaveBeenCalledWith(['/contacts/new'], { queryParams: { clientId: 1 } });
  });

  it('opens or edits a contact', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    (fixture.nativeElement.querySelector('tr.contact-row') as HTMLElement).click();
    expect(navigate).toHaveBeenCalledWith(['/contacts', 2]);
    component.onEdit(zoe);
    expect(navigate).toHaveBeenCalledWith(['/contacts', 1, 'edit']);
  });

  describe('deleting a contact', () => {
    it('refuses a contact without id', () => {
      const component = create().componentInstance;

      component.onDelete({ ...alice, id: undefined });

      expect(notifications.error).toHaveBeenCalledWith('errors.missingContactId');
      expect(confirm.confirm).not.toHaveBeenCalled();
    });

    it('keeps the contact when the deletion is not confirmed', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(false));

      component.onDelete(alice);

      expect(confirm.confirm).toHaveBeenCalledWith({
        messageKey: 'contacts.confirmDelete', messageParams: { name: 'Alice Martin' }, danger: true
      });
      expect(contacts.delete).not.toHaveBeenCalled();
    });

    it('deletes the contact and reloads the list', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      contacts.delete.and.returnValue(of(undefined));
      contacts.getForCurrentFreelance.and.returnValue(of([bob]));

      component.onDelete(alice);

      expect(contacts.delete).toHaveBeenCalledWith(2);
      expect(notifications.success).toHaveBeenCalledWith('contacts.deleteSuccess');
      expect(names(component)).toEqual(['Bob']);
    });

    it('reports a failed deletion', () => {
      spyOn(console, 'error');
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      contacts.delete.and.returnValue(throwError(() => new Error('down')));

      component.onDelete(alice);

      expect(notifications.error).toHaveBeenCalledWith('errors.deletingContact');
    });
  });

  it('deletes the picked contacts once confirmed', () => {
    const fixture = create();
    const element = fixture.nativeElement as HTMLElement;
    contacts.deleteAll.and.returnValue(of({ deleted: 1, kept: 0 }));
    confirm.confirm.and.returnValue(of(true));

    element.querySelector<HTMLInputElement>('td.select-cell input')!.click();
    fixture.detectChanges();
    (element.querySelector('app-list-selection-bar .delete-selection') as HTMLButtonElement).click();

    expect(contacts.deleteAll).toHaveBeenCalledWith([2]);
    expect(notifications.success).toHaveBeenCalledWith('contacts.bulkDeleted', 5000, { deleted: 1, kept: 0 });
    expect(navigate).not.toHaveBeenCalled();
  });
});
