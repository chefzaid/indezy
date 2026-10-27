import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ClientDto } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { ClientService } from '../../../services/client/client.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ClientFormComponent } from './client-form.component';

describe('ClientFormComponent', () => {
  let clients: jasmine.SpyObj<ClientService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let navigate: jasmine.Spy;

  function create(id?: number, type?: string): ClientFormComponent {
    clients = jasmine.createSpyObj('ClientService', ['getById', 'create', 'update']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ClientFormComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: {
          params: of(id ? { id } : {}),
          snapshot: { queryParamMap: convertToParamMap(type ? { type } : {}) }
        } },
        { provide: ClientService, useValue: clients },
        { provide: AuthService, useValue: { getUser: () => ({ id: 7 }) } },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate');
    return TestBed.createComponent(ClientFormComponent).componentInstance;
  }

  it('creates an ESN with the default activity report template', () => {
    const component = create(undefined, 'esn');
    clients.create.and.returnValue(of({ id: 9 } as ClientDto));
    component.ngOnInit();
    component.clientForm.patchValue({ companyName: ' Capgemini ', city: 'Paris' });

    component.onSubmit();

    expect(clients.create).toHaveBeenCalledWith(jasmine.objectContaining({
      companyName: 'Capgemini', isFinal: false, freelanceId: 7, activityReportTitle: undefined,
      activityReportLayout: 'DAILY_TABLE', activityReportLanguage: 'FR', activityReportShowsLocation: true,
      activityReportFooter: undefined
    }));
    expect(navigate).toHaveBeenCalledWith(['/clients', 9]);
  });

  it('loads and saves the client\'s activity report template', () => {
    const component = create(9);
    clients.getById.and.returnValue(of({
      id: 9, companyName: 'Capgemini', city: 'Paris', isFinal: false, freelanceId: 7,
      activityReportTitle: 'Relevé mensuel', activityReportLayout: 'CALENDAR_GRID', activityReportLanguage: 'EN',
      activityReportShowsLocation: false, activityReportFooter: 'Avant le 25'
    } as ClientDto));
    clients.update.and.returnValue(of({ id: 9 } as ClientDto));
    component.ngOnInit();

    expect(component.clientForm.value).toEqual(jasmine.objectContaining({
      activityReportTitle: 'Relevé mensuel', activityReportLayout: 'CALENDAR_GRID', activityReportLanguage: 'EN',
      activityReportShowsLocation: false, activityReportFooter: 'Avant le 25'
    }));
    component.clientForm.patchValue({ activityReportTitle: '  ', activityReportFooter: ' Retour avant le 20 ' });
    component.onSubmit();

    expect(clients.update).toHaveBeenCalledWith(9, jasmine.objectContaining({
      id: 9, activityReportTitle: undefined, activityReportLayout: 'CALENDAR_GRID', activityReportShowsLocation: false,
      activityReportFooter: 'Retour avant le 20'
    }));
  });

  it('defaults the template of a client saved before templates existed', () => {
    const component = create(9);
    clients.getById.and.returnValue(of({ id: 9, companyName: 'Acme', city: 'Lyon', isFinal: true } as ClientDto));
    component.ngOnInit();

    expect(component.clientForm.value).toEqual(jasmine.objectContaining({
      activityReportTitle: '', activityReportLayout: 'DAILY_TABLE', activityReportLanguage: 'FR',
      activityReportShowsLocation: true, activityReportFooter: ''
    }));
  });

  it('refuses a template title or footer over its maximum length', () => {
    const component = create();
    component.clientForm.patchValue({
      companyName: 'Acme', city: 'Lyon', activityReportTitle: 'x'.repeat(121), activityReportFooter: 'x'.repeat(501)
    });
    component.clientForm.markAllAsTouched();

    component.onSubmit();

    expect(clients.create).not.toHaveBeenCalled();
    expect(component.getFieldError('activityReportTitle')).toBe('errors.maxLength');
    expect(component.getFieldError('activityReportFooter')).toBe('errors.maxLength');
  });

  it('saves the client\'s SIREN and VAT number as typed in groups, and refuses malformed ones', () => {
    const component = create(9);
    clients.getById.and.returnValue(of({ id: 9, companyName: 'Acme', city: 'Lyon', isFinal: true, freelanceId: 7,
      siren: '552100554', vatNumber: 'FR40552100554' } as ClientDto));
    clients.update.and.returnValue(of({ id: 9 } as ClientDto));
    component.ngOnInit();
    expect(component.clientForm.value.siren).toBe('552100554');

    component.clientForm.patchValue({ siren: '123 456', vatNumber: '40' });
    component.onSubmit();
    expect(clients.update).not.toHaveBeenCalled();
    expect(component.getFieldError('siren')).toBe('errors.billing.siren');
    expect(component.getFieldError('vatNumber')).toBe('errors.billing.vatNumber');

    component.clientForm.patchValue({ siren: '552 100 554', vatNumber: 'fr 40 552100554' });
    component.onSubmit();
    expect(clients.update).toHaveBeenCalledWith(9, jasmine.objectContaining({ siren: '552100554', vatNumber: 'FR40552100554' }));
  });

  it('reports a failed save', () => {
    const component = create();
    clients.create.and.returnValue(throwError(() => new Error('down')));
    component.clientForm.patchValue({ companyName: 'Acme', city: 'Lyon' });

    component.onSubmit();

    expect(notifications.error).toHaveBeenCalledWith('errors.creatingClient');
    expect(component.isSaving).toBeFalse();
  });

  it('clears the blacklist reason when the client is no longer blacklisted', () => {
    const component = create();
    component.ngOnInit();
    component.clientForm.patchValue({ isBlacklisted: true, blacklistReason: 'Late payments' });

    component.clientForm.patchValue({ isBlacklisted: false });

    expect(component.clientForm.value.blacklistReason).toBe('');
  });

  it('returns to the list when the client cannot be loaded', () => {
    const component = create(9);
    spyOn(console, 'error');
    clients.getById.and.returnValue(throwError(() => ({ status: 404 })));
    component.ngOnInit();

    expect(notifications.error).toHaveBeenCalledWith('errors.clientNotFound');
    expect(navigate).toHaveBeenCalledWith(['/clients']);
    expect(component.isLoading).toBeFalse();
  });

  it('cancels back to the client being edited, or to the list', () => {
    const editing = create(9);
    clients.getById.and.returnValue(of({ id: 9, companyName: 'Acme', city: 'Lyon', isFinal: true } as ClientDto));
    editing.ngOnInit();
    editing.onCancel();
    expect(navigate).toHaveBeenCalledWith(['/clients', 9]);

    TestBed.resetTestingModule();
    const creating = create();
    creating.onCancel();
    expect(navigate).toHaveBeenCalledWith(['/clients']);
  });
});
