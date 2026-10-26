import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { ClientDto, ContactDto, PROJECT_STATUS_COLORS, ProjectDto, ProjectStatus } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientService } from '../../../services/client/client.service';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ContactImportDialogComponent } from '../../contacts/contact-import-dialog/contact-import-dialog.component';
import { ClientDetailComponent } from './client-detail.component';

describe('ClientDetailComponent', () => {
  let clients: jasmine.SpyObj<ClientService>;
  let contacts: jasmine.SpyObj<ContactService>;
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let navigate: jasmine.Spy;

  const client: ClientDto = {
    id: 9, companyName: 'Capgemini', city: 'Paris', isFinal: false, isBlacklisted: true, blacklistReason: 'Late payments',
    rating: 4, domain: 'capgemini.com', notes: 'Big ESN', averageDailyRate: 610
  };
  const contact = (id: number | undefined, firstName: string, overrides: Partial<ContactDto> = {}): ContactDto =>
    ({ id, firstName, lastName: 'Martin', clientId: 9, ...overrides });
  const project = (id: number, overrides: Partial<ProjectDto> = {}): ProjectDto =>
    ({ id, role: `Role ${id}`, dailyRate: 600, clientId: 9, ...overrides } as ProjectDto);

  interface Options { id?: number; tab?: string; userId?: number; projectList?: ProjectDto[]; load?: Observable<ClientDto> }

  function create(options: Options = {}): ComponentFixture<ClientDetailComponent> {
    const { id = 9, tab, userId = 7 } = options;
    clients = jasmine.createSpyObj('ClientService', ['getById', 'delete']);
    contacts = jasmine.createSpyObj('ContactService', ['getByClientId', 'delete', 'importForClient', 'getClientRelations']);
    contacts.getClientRelations.and.returnValue(of([]));
    projects = jasmine.createSpyObj('ProjectService', ['getByFreelanceId', 'getByClientId']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    clients.getById.and.returnValue(options.load ?? of(client));
    contacts.getByClientId.and.returnValue(of([contact(1, 'Alice', { email: 'alice@acme.fr', phone: '0601' }), contact(2, 'Bob')]));
    const list = options.projectList ?? [];
    projects.getByFreelanceId.and.returnValue(of(list));
    projects.getByClientId.and.returnValue(of(list));
    TestBed.configureTestingModule({
      imports: [ClientDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: {
          params: of(id ? { id } : {}),
          snapshot: { queryParamMap: convertToParamMap(tab ? { tab } : {}) }
        } },
        { provide: ClientService, useValue: clients },
        { provide: ContactService, useValue: contacts },
        { provide: ProjectService, useValue: projects },
        { provide: AuthService, useValue: { getUser: () => (userId ? { id: userId } : null) } },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(ClientDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ClientDetailComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  it('shows the client with its own and intermediated missions, newest first', () => {
    const fixture = create({ projectList: [
      project(1, { startDate: '2025-01-01' }),
      project(2, { clientId: 3, middlemanId: 9, clientName: 'BNP', startDate: '2026-03-01' }),
      project(3, { clientId: 4 }),
      project(4)
    ] });
    const component = fixture.componentInstance;

    expect(clients.getById).toHaveBeenCalledWith(9);
    expect(contacts.getByClientId).toHaveBeenCalledWith(9);
    expect(projects.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(component.projects.map(p => p.id)).toEqual([2, 1, 4]);
    expect(component.isLoading).toBeFalse();
    const page = text(fixture);
    expect(page).toContain('Capgemini');
    expect(page).toContain('clients.esn');
    expect(page).toContain('clients.form.blacklisted');
    expect(page).toContain('4/5');
    expect(page).toContain('Late payments');
    expect(page).toContain('Big ESN');
    expect(page).toContain('clients.detail.averageRate');
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[target="_blank"]');
    expect(link.href).toBe('https://capgemini.com/');
  });

  it('asks for the client\'s missions when nobody is signed in', () => {
    create({ userId: 0, projectList: [project(1)] });

    expect(projects.getByClientId).toHaveBeenCalledWith(9);
    expect(projects.getByFreelanceId).not.toHaveBeenCalled();
  });

  it('loads nothing without a client id', () => {
    const component = create({ id: 0 }).componentInstance;

    expect(clients.getById).not.toHaveBeenCalled();
    component.onAddContact();
    component.onEditContact(contact(1, 'Alice'));
    component.onImportContacts();
    component.onEdit();
    component.onDelete();
    expect(navigate).not.toHaveBeenCalled();
    expect(confirm.confirm).not.toHaveBeenCalled();
  });

  it('lists the missions with the client they were intermediated for', () => {
    const fixture = create({ tab: 'projects', projectList: [
      project(1, { status: ProjectStatus.WON, startDate: '2026-01-01' }),
      project(2, { clientId: 3, middlemanId: 9, clientName: 'BNP' })
    ] });
    const component = fixture.componentInstance;

    expect(component.selectedTabIndex).toBe(1);
    expect(text(fixture)).toContain('Role 1');
    expect(text(fixture)).toContain('projects.statuses.WON');
    expect(text(fixture)).toContain('clients.detail.forClient');
    expect(component.isIntermediaryFor(component.projects[0])).toBeFalse();
    expect(component.isIntermediaryFor(component.projects[1])).toBeTrue();
    expect(component.getProjectStatusColor(component.projects[0])).toBe(PROJECT_STATUS_COLORS.WON);
    expect(component.getProjectStatusColor(component.projects[1])).toBe(PROJECT_STATUS_COLORS.CONTACT);

    (fixture.nativeElement.querySelector('tr.clickable-row') as HTMLElement).click();
    expect(navigate).toHaveBeenCalledWith(['/projects', 1]);
  });

  it('shows empty missions and contacts tabs', () => {
    const fixture = create({ tab: 'projects' });
    expect(text(fixture)).toContain('clients.detail.noProjects');

    fixture.componentInstance.contacts = [];
    fixture.componentInstance.selectedTabIndex = 2;
    expect(text(fixture)).toContain('clients.noContactsSubtitle');
  });

  it('opens the tab named in the address and keeps the selected tab in it', () => {
    const component = create({ tab: 'contacts' }).componentInstance;
    expect(component.selectedTabIndex).toBe(2);

    component.onTabChange(1);
    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      queryParams: { tab: 'projects' }, queryParamsHandling: 'merge', replaceUrl: true
    }));
    component.onTabChange(0);
    expect(navigate.calls.mostRecent().args[1].queryParams).toEqual({ tab: null });
    expect(component.selectedTabIndex).toBe(0);

    TestBed.resetTestingModule();
    expect(create({ tab: 'unknown' }).componentInstance.selectedTabIndex).toBe(0);
  });

  it('lists the contacts and opens, edits or adds one', () => {
    const fixture = create({ tab: 'contacts' });
    const component = fixture.componentInstance;
    const page = text(fixture);

    expect(page).toContain('Alice Martin');
    expect(page).toContain('Bob Martin');
    expect(fixture.nativeElement.querySelector('a[href="mailto:alice@acme.fr"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="tel:0601"]')).not.toBeNull();

    (fixture.nativeElement.querySelector('tr.clickable-row') as HTMLElement).click();
    expect(navigate).toHaveBeenCalledWith(['/contacts', 1]);
    component.onEditContact(component.contacts[1]);
    expect(navigate).toHaveBeenCalledWith(['/clients', 9, 'contacts', 2, 'edit']);
    component.onAddContact();
    expect(navigate).toHaveBeenCalledWith(['/clients', 9, 'contacts', 'new']);
  });

  it('navigates back, to edit and to a new mission', () => {
    const component = create().componentInstance;

    component.onBack();
    expect(navigate).toHaveBeenCalledWith(['/clients']);
    component.onEdit();
    expect(navigate).toHaveBeenCalledWith(['/clients', 9, 'edit']);
    component.onNewProject();
    expect(navigate).toHaveBeenCalledWith(['/projects/new']);
  });

  it('returns to the list when the client does not exist', () => {
    spyOn(console, 'error');
    const component = create({ load: throwError(() => ({ status: 404 })) }).componentInstance;

    expect(notifications.error).toHaveBeenCalledWith('errors.clientNotFound');
    expect(navigate).toHaveBeenCalledWith(['/clients']);
    expect(component.isLoading).toBeFalse();
  });

  it('stays on the page when the client fails to load', () => {
    spyOn(console, 'error');
    const fixture = create({ load: throwError(() => ({ status: 500 })) });

    expect(notifications.error).toHaveBeenCalledWith('errors.loadingClient');
    expect(navigate).not.toHaveBeenCalled();
    expect(text(fixture)).not.toContain('Capgemini');
  });

  describe('website link', () => {
    it('adds a scheme, keeps an existing one and ignores values that are not addresses', () => {
      const component = create().componentInstance;
      const withDomain = (domain?: string): string | null => {
        component.client = { ...client, domain };
        return component.websiteUrl();
      };

      expect(withDomain(' acme.fr ')).toBe('https://acme.fr');
      expect(withDomain('http://acme.fr')).toBe('http://acme.fr');
      expect(withDomain('localhost')).toBeNull();
      expect(withDomain(undefined)).toBeNull();
      component.client = undefined;
      expect(component.websiteUrl()).toBeNull();
    });

    it('shows a plain domain that is not a link', () => {
      const fixture = create({ load: of({ ...client, domain: 'intranet', isFinal: true, isBlacklisted: false }) });

      expect(text(fixture)).toContain('intranet');
      expect(text(fixture)).toContain('clients.finalClient');
      expect(fixture.nativeElement.querySelector('a[target="_blank"]')).toBeNull();
    });
  });

  describe('deleting the client', () => {
    it('refuses while missions reference it', () => {
      const component = create({ projectList: [project(1)] }).componentInstance;

      component.onDelete();

      expect(notifications.error).toHaveBeenCalledWith('clients.deleteInUse', 5000);
      expect(confirm.confirm).not.toHaveBeenCalled();
    });

    it('does nothing when the deletion is not confirmed', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(false));

      component.onDelete();

      expect(confirm.confirm).toHaveBeenCalledWith({
        messageKey: 'clients.confirmDelete', messageParams: { name: 'Capgemini' }, danger: true
      });
      expect(clients.delete).not.toHaveBeenCalled();
    });

    it('deletes and returns to the list', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      clients.delete.and.returnValue(of(undefined));

      component.onDelete();

      expect(clients.delete).toHaveBeenCalledWith(9);
      expect(notifications.success).toHaveBeenCalledWith('clients.deleteSuccess');
      expect(navigate).toHaveBeenCalledWith(['/clients']);
    });

    it('explains a refused or failed deletion', () => {
      spyOn(console, 'error');
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      clients.delete.and.returnValues(throwError(() => ({ status: 409 })), throwError(() => ({ status: 500 })));

      component.onDelete();
      expect(notifications.error).toHaveBeenCalledWith('clients.deleteInUse', 5000);
      component.onDelete();
      expect(notifications.error).toHaveBeenCalledWith('errors.deletingClient', 5000);
      expect(navigate).not.toHaveBeenCalled();
    });
  });

  describe('importing contacts', () => {
    let open: jasmine.Spy;

    function setUp(result: string | undefined): ClientDetailComponent {
      const fixture = create();
      open = spyOn(fixture.debugElement.injector.get(MatDialog), 'open')
        .and.returnValue({ afterClosed: () => of(result) } as MatDialogRef<unknown>);
      return fixture.componentInstance;
    }

    it('does nothing when the dialog is dismissed', () => {
      const component = setUp(undefined);

      component.onImportContacts();

      expect(open).toHaveBeenCalledWith(ContactImportDialogComponent, { width: '560px', autoFocus: false });
      expect(contacts.importForClient).not.toHaveBeenCalled();
    });

    it('imports the pasted contacts and reloads them', () => {
      const component = setUp('Alice;alice@acme.fr');
      contacts.importForClient.and.returnValue(of({ imported: 3, skipped: 1, total: 4 }));
      contacts.getByClientId.and.returnValue(of([contact(5, 'Chloe')]));

      component.onImportContacts();

      expect(contacts.importForClient).toHaveBeenCalledWith(9, 'Alice;alice@acme.fr');
      expect(notifications.success).toHaveBeenCalledWith('contacts.import.result', 4000, { imported: 3, skipped: 1 });
      expect(component.contacts.map(c => c.id)).toEqual([5]);
      expect(component.isLoadingContacts).toBeFalse();
    });

    it('reports a failed import', () => {
      const component = setUp('garbage');
      contacts.importForClient.and.returnValue(throwError(() => new Error('down')));

      component.onImportContacts();

      expect(notifications.error).toHaveBeenCalledWith('contacts.import.error');
    });

    it('reports contacts that cannot be reloaded', () => {
      spyOn(console, 'error');
      const component = setUp('Alice');
      contacts.importForClient.and.returnValue(of({ imported: 1, skipped: 0, total: 1 }));
      contacts.getByClientId.and.returnValue(throwError(() => new Error('down')));

      component.onImportContacts();

      expect(notifications.error).toHaveBeenCalledWith('errors.loadingContacts');
      expect(component.isLoadingContacts).toBeFalse();
    });
  });

  describe('deleting a contact', () => {
    it('refuses a contact without id', () => {
      const component = create().componentInstance;

      component.onDeleteContact(contact(undefined, 'Ghost'));

      expect(notifications.error).toHaveBeenCalledWith('errors.missingContactId');
      expect(confirm.confirm).not.toHaveBeenCalled();
    });

    it('keeps the contact when the deletion is not confirmed', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(false));

      component.onDeleteContact(contact(1, 'Alice'));

      expect(confirm.confirm).toHaveBeenCalledWith({
        messageKey: 'contacts.confirmDelete', messageParams: { name: 'Alice Martin' }, danger: true
      });
      expect(contacts.delete).not.toHaveBeenCalled();
    });

    it('deletes the contact and reloads the list', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      contacts.delete.and.returnValue(of(undefined));
      contacts.getByClientId.calls.reset();

      component.onDeleteContact(contact(1, 'Alice', { lastName: undefined }));

      expect(contacts.delete).toHaveBeenCalledWith(1);
      expect(notifications.success).toHaveBeenCalledWith('contacts.deleteSuccess');
      expect(contacts.getByClientId).toHaveBeenCalledWith(9);
    });

    it('reports a failed deletion', () => {
      spyOn(console, 'error');
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      contacts.delete.and.returnValue(throwError(() => new Error('down')));

      component.onDeleteContact(contact(1, 'Alice'));

      expect(notifications.error).toHaveBeenCalledWith('errors.deletingContact');
    });
  });
});
