import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { ClientDto } from '../../../models';
import { ClientService } from '../../../services/client/client.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ClientListComponent } from './client-list.component';

describe('ClientListComponent', () => {
  let clients: jasmine.SpyObj<ClientService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let navigate: jasmine.Spy;

  const acme: ClientDto = { id: 1, companyName: 'Acme', city: 'Lyon', isFinal: true, rating: 5, totalProjects: 1,
    domain: 'acme.fr' };
  const capgemini: ClientDto = { id: 2, companyName: 'capgemini', city: 'Paris', isFinal: false, rating: 3, totalProjects: 4,
    notes: 'Grand compte' };
  const bnp: ClientDto = { id: 3, companyName: 'BNP', city: 'Paris', isFinal: true, isBlacklisted: true,
    blacklistReason: 'Late payments' };
  const all = [acme, capgemini, bnp];

  function create(load: Observable<ClientDto[]> = of(all)): ComponentFixture<ClientListComponent> {
    clients = jasmine.createSpyObj('ClientService', ['getForCurrentFreelance', 'delete']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    clients.getForCurrentFreelance.and.returnValue(load);
    TestBed.configureTestingModule({
      imports: [ClientListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ClientService, useValue: clients },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(ClientListComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ClientListComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function names(component: ClientListComponent): string[] {
    return component.filteredClients.map(client => client.companyName);
  }

  function filter(component: ClientListComponent, values: Record<string, unknown>): string[] {
    component.filterForm.patchValue(values, { emitEvent: false });
    component.applyFilters();
    return names(component);
  }

  it('lists the clients by name with their cities', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    expect(names(component)).toEqual(['Acme', 'BNP', 'capgemini']);
    expect(component.cities).toEqual(['Lyon', 'Paris']);
    expect(component.isLoading).toBeFalse();
    const page = text(fixture);
    expect(page).toContain('Acme');
    expect(page).toContain('clients.esn');
    expect(page).toContain('clients.finalClient');
    expect(page).toContain('5/5');
    expect(fixture.nativeElement.querySelectorAll('tr.client-row').length).toBe(3);
    expect(fixture.nativeElement.querySelector('a.website-link').getAttribute('href')).toBe('https://acme.fr');
    expect(fixture.nativeElement.querySelector('mat-paginator')).toBeNull();
  });

  it('reports clients that cannot be loaded', () => {
    spyOn(console, 'error');
    const component = create(throwError(() => new Error('down'))).componentInstance;

    expect(notifications.error).toHaveBeenCalledWith('errors.loadingClients');
    expect(component.isLoading).toBeFalse();
  });

  it('invites to add a first client when there is none', () => {
    const fixture = create(of([]));

    expect(text(fixture)).toContain('clients.startFirstClient');
    expect(fixture.componentInstance.hasActiveFilters()).toBeFalse();
  });

  it('filters by type, city and search text', () => {
    const component = create().componentInstance;

    expect(filter(component, { type: 'FINAL' })).toEqual(['Acme', 'BNP']);
    expect(filter(component, { type: 'ESN' })).toEqual(['capgemini']);
    expect(filter(component, { type: 'BLACKLISTED' })).toEqual(['BNP']);
    expect(filter(component, { type: 'ALL', city: 'Paris' })).toEqual(['BNP', 'capgemini']);
    expect(filter(component, { city: '', searchQuery: ' GRAND ' })).toEqual(['capgemini']);
    expect(filter(component, { searchQuery: 'acme.fr' })).toEqual(['Acme']);
    expect(component.hasActiveFilters()).toBeTrue();
  });

  it('applies filters typed in the form after a short pause', fakeAsync(() => {
    const fixture = create();
    const component = fixture.componentInstance;

    component.filterForm.patchValue({ searchQuery: 'zzz' });
    tick(150);

    expect(component.filteredClients).toEqual([]);
    expect(text(fixture)).toContain('clients.tryDifferentCriteria');

    (fixture.nativeElement.querySelector('.no-data button') as HTMLButtonElement).click();
    tick(150);
    expect(component.filterForm.value.searchQuery).toBe('');
    expect(names(component).length).toBe(3);
    expect(component.hasActiveFilters()).toBeFalse();
  }));

  it('sorts by city, rating and number of missions in either order', () => {
    const component = create().componentInstance;

    expect(filter(component, { sortBy: 'city', sortOrder: 'asc' })[0]).toBe('Acme');
    expect(filter(component, { sortBy: 'rating', sortOrder: 'desc' })).toEqual(['Acme', 'capgemini', 'BNP']);
    expect(filter(component, { sortBy: 'projects', sortOrder: 'desc' })).toEqual(['capgemini', 'Acme', 'BNP']);
    expect(filter(component, { sortBy: 'name', sortOrder: 'desc' })).toEqual(['capgemini', 'BNP', 'Acme']);
  });

  it('pages through many clients and returns to the first page when filtering', () => {
    const many: ClientDto[] = Array.from({ length: 30 }, (_, i) =>
      ({ id: i + 1, companyName: `Client ${String(i + 1).padStart(2, '0')}`, city: 'Nantes', isFinal: true }));
    const fixture = create(of(many));
    const component = fixture.componentInstance;

    expect(component.pagedClients.length).toBe(25);
    expect(text(fixture)).toContain('Client 01');
    expect(fixture.nativeElement.querySelector('mat-paginator')).not.toBeNull();

    component.onPageChange({ pageIndex: 1, pageSize: 25, length: 30 });
    expect(component.pagedClients.map(c => c.companyName)).toEqual(
      ['Client 26', 'Client 27', 'Client 28', 'Client 29', 'Client 30']);

    component.onPageChange({ pageIndex: 3, pageSize: 50, length: 30 });
    expect(component.pageIndex).toBe(0);
    expect(component.pagedClients.length).toBe(30);
  });

  it('opens a client when its row is clicked', () => {
    const fixture = create();

    (fixture.nativeElement.querySelector('tr.client-row') as HTMLElement).click();

    expect(navigate).toHaveBeenCalledWith(['/clients', 1]);
  });

  it('builds website links only for addresses', () => {
    const component = create().componentInstance;

    expect(component.websiteUrl({ ...acme, domain: 'https://acme.fr' })).toBe('https://acme.fr');
    expect(component.websiteUrl({ ...acme, domain: ' acme.fr ' })).toBe('https://acme.fr');
    expect(component.websiteUrl({ ...acme, domain: 'acme' })).toBeNull();
    expect(component.websiteUrl({ ...acme, domain: undefined })).toBeNull();
  });

  describe('deleting a client', () => {
    it('keeps the client when the deletion is not confirmed', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(false));

      component.deleteClient(acme);

      expect(confirm.confirm).toHaveBeenCalledWith({
        messageKey: 'clients.confirmDelete', messageParams: { name: 'Acme' }, danger: true
      });
      expect(clients.delete).not.toHaveBeenCalled();
    });

    it('deletes the client and reloads the list', () => {
      const fixture = create();
      confirm.confirm.and.returnValue(of(true));
      clients.delete.and.returnValue(of(undefined));
      clients.getForCurrentFreelance.and.returnValue(of([capgemini]));

      (fixture.nativeElement.querySelector('.action-buttons button:last-child') as HTMLButtonElement).click();

      expect(clients.delete).toHaveBeenCalledWith(1);
      expect(notifications.success).toHaveBeenCalledWith('clients.deleteSuccess');
      expect(names(fixture.componentInstance)).toEqual(['capgemini']);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('explains a refused or failed deletion', () => {
      spyOn(console, 'error');
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      clients.delete.and.returnValues(throwError(() => ({ status: 409 })), throwError(() => ({ status: 500 })));

      component.deleteClient(acme);
      expect(notifications.error).toHaveBeenCalledWith('clients.deleteInUse', 5000);
      component.deleteClient(acme);
      expect(notifications.error).toHaveBeenCalledWith('errors.deletingClient', 5000);
    });
  });
});
