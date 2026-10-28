import { LOCALE_ID } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { Clipboard } from '@angular/cdk/clipboard';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { ActivityReport, ActivityReportLink, Invoice, ProjectDto, ProjectStatus } from '../../../models';
import { ActivityReportService } from '../../../services/activity-report/activity-report.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ActivityReportCardComponent } from './activity-report-card.component';

describe('ActivityReportCardComponent', () => {
  let service: jasmine.SpyObj<ActivityReportService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirmDialog: jasmine.SpyObj<ConfirmDialogService>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  let invoices: jasmine.SpyObj<InvoiceService>;
  let navigate: jasmine.Spy;

  const mission: ProjectDto = {
    id: 42, role: 'Développeur Java', status: ProjectStatus.WON, dailyRate: 600, workMode: 'HYBRID',
    startDate: '2026-09-01', durationInMonths: 6, clientName: 'Société Générale', middlemanName: 'Capgemini'
  } as ProjectDto;

  function report(overrides: Partial<ActivityReport> = {}): ActivityReport {
    return {
      projectId: 42, year: 2026, month: 11, status: 'DRAFT', days: [],
      publicHolidays: [{ date: '2026-11-01', holiday: 'ALL_SAINTS' }, { date: '2026-11-11', holiday: 'ARMISTICE' }],
      businessDays: 20, workedDays: 0, onsiteDays: 0, remoteDays: 0, absenceDays: 0, dailyRate: 600, amount: 0,
      missionStart: '2026-09-01', missionEnd: '2027-02-28', ...overrides
    };
  }

  function create(project: ProjectDto = mission, current: ActivityReport = report()): ComponentFixture<ActivityReportCardComponent> {
    registerLocaleData(localeFr);
    service = jasmine.createSpyObj('ActivityReportService',
      ['getReports', 'getReport', 'save', 'submit', 'renewLink', 'reopen', 'downloadPdf']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirmDialog = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    invoices = jasmine.createSpyObj('InvoiceService', ['fromReport']);
    service.getReports.and.returnValue(of([]));
    service.getReport.and.returnValue(of(current));
    confirmDialog.confirm.and.returnValue(of(true));
    TestBed.configureTestingModule({
      imports: [ActivityReportCardComponent, TranslateModule.forRoot()],
      providers: [
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: ActivityReportService, useValue: service },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirmDialog },
        { provide: Clipboard, useValue: clipboard },
        { provide: InvoiceService, useValue: invoices },
        provideRouter([])
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(ActivityReportCardComponent);
    fixture.componentRef.setInput('project', project);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ActivityReportCardComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).innerText.replace(/\s+/g, ' ');
  }

  function button(fixture: ComponentFixture<ActivityReportCardComponent>, label: string): HTMLButtonElement {
    fixture.detectChanges();
    const found = [...fixture.nativeElement.querySelectorAll('button, a')]
      .find((element: HTMLElement) => element.textContent?.includes(label));
    if (!found) {
      throw new Error(`No button ${label}`);
    }
    return found as HTMLButtonElement;
  }

  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  it('opens the current month of the mission with its calendar and totals', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();

    expect(service.getReport).toHaveBeenCalledWith(42, 2026, 11);
    expect(service.getReports).toHaveBeenCalledWith(42);
    expect(text(fixture)).toContain('Novembre 2026');
    expect(text(fixture)).toContain('Brouillon');
    expect(text(fixture)).toContain('Jours ouvrés 20');
    expect(text(fixture)).toContain('Le PDF suit le modèle de compte rendu défini sur la fiche de Capgemini.');
    expect(fixture.nativeElement.querySelectorAll('app-activity-calendar button.day').length).toBe(30);
  });

  it('starts on the first month of a mission that has not begun, and on the last one of a finished mission', () => {
    jasmine.clock().mockDate(new Date(2026, 5, 15));
    create();
    expect(service.getReport).toHaveBeenCalledWith(42, 2026, 9);

    TestBed.resetTestingModule();
    jasmine.clock().mockDate(new Date(2027, 6, 1));
    create();
    expect(service.getReport).toHaveBeenCalledWith(42, 2027, 2);
  });

  it('records days with the chosen brush, fills business days and shows live totals and amount', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();
    const component = fixture.componentInstance;

    component.fill();
    expect(component.days.length).toBe(20);
    expect(component.days.every(day => day.type === 'ONSITE')).toBeTrue();
    component.brush = 'HALF_REMOTE';
    component.onDayToggled('2026-11-13');
    component.brush = 'ABSENCE';
    component.onDayToggled('2026-11-20');

    expect(component.dirty).toBeTrue();
    expect(text(fixture)).toContain('Jours travaillés 18,5');
    expect(fixture.nativeElement.querySelector('[data-total="amount"]').textContent).toMatch(/11\s100\s€/);
    component.clearAll();
    expect(component.days).toEqual([]);
  });

  it('fills remote days for a remote mission', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create({ ...mission, workMode: 'REMOTE' } as ProjectDto);

    expect(fixture.componentInstance.brush).toBe('REMOTE');
    fixture.componentInstance.fill();
    expect(fixture.componentInstance.days[0].type).toBe('REMOTE');
  });

  it('saves the days and the trimmed comment', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();
    const component = fixture.componentInstance;
    const day = { date: '2026-11-02', type: 'ONSITE' as const, halfDay: false };
    service.save.and.returnValue(of(report({ id: 7, days: [day], comment: 'Formation', workedDays: 1 })));
    component.onDayToggled('2026-11-02');
    component.comment = '  Formation  ';

    button(fixture, 'Enregistrer').click();

    expect(service.save).toHaveBeenCalledWith(42, 2026, 11, { comment: 'Formation', days: [day] });
    expect(component.dirty).toBeFalse();
    expect(component.report?.id).toBe(7);
    expect(notifications.success).toHaveBeenCalledWith('activityReports.saved', 2500);
    expect(service.getReports).toHaveBeenCalledTimes(2);
  });

  it('reports a failed save', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();
    service.save.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.onDayToggled('2026-11-02');

    fixture.componentInstance.save();

    expect(notifications.error).toHaveBeenCalledWith('activityReports.saveError');
    expect(fixture.componentInstance.isBusy).toBeFalse();
  });

  it('saves pending changes before submitting, then shows the link to copy or email', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();
    const component = fixture.componentInstance;
    const saved = report({ id: 7, days: [{ date: '2026-11-02', type: 'ONSITE', halfDay: false }], workedDays: 1 });
    service.save.and.returnValue(of(saved));
    const link: ActivityReportLink = {
      token: 'secret-token', expiresAt: '2026-12-15T10:00:00Z', report: { ...saved, status: 'SUBMITTED' }
    };
    service.submit.and.returnValue(of(link));
    clipboard.copy.and.returnValue(true);
    component.onDayToggled('2026-11-02');

    button(fixture, 'Envoyer pour validation').click();

    expect(service.save).toHaveBeenCalled();
    expect(service.submit).toHaveBeenCalledWith(42, 2026, 11);
    expect(component.link).toMatch(/\/activity-report#secret-token$/);
    expect(text(fixture)).toContain('En attente de validation');
    expect(text(fixture)).toContain('Copiez-le maintenant');
    const input = fixture.nativeElement.querySelector('[data-validation-link]') as HTMLInputElement;
    expect(input.value).toBe(component.link!);
    expect(fixture.nativeElement.querySelector('app-activity-calendar button.day')).toBeNull();

    button(fixture, 'Copier le lien').click();
    expect(clipboard.copy).toHaveBeenCalledWith(component.link!);
    expect(notifications.success).toHaveBeenCalledWith('activityReports.link.copied');

    const mailto = decodeURIComponent(component.mailtoHref);
    expect(mailto).toContain('subject=Compte rendu d\'activité de Novembre 2026 – Développeur Java');
    expect(mailto).toContain('1 jour(s) travaillé(s)');
    expect(mailto).toContain(component.link!);
  });

  it('submits a saved month without saving it again and reports a failed submission', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create(mission, report({ id: 7, days: [{ date: '2026-11-02', type: 'ONSITE', halfDay: false }] }));
    service.submit.and.returnValue(throwError(() => new Error('down')));

    fixture.componentInstance.submit();

    expect(service.save).not.toHaveBeenCalled();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.submitError');
  });

  it('offers a new link for a submitted month whose link is no longer on screen', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const submitted = report({ id: 7, status: 'SUBMITTED', linkExpiresAt: '2026-12-15T10:00:00Z' });
    const fixture = create(mission, submitted);
    service.renewLink.and.returnValue(of({ token: 'renewed', expiresAt: '2026-12-20T10:00:00Z', report: submitted }));

    expect(text(fixture)).toContain('Le lien envoyé reste valable jusqu\'au 15 déc. 2026');
    button(fixture, 'Générer un nouveau lien').click();

    expect(fixture.componentInstance.link).toMatch(/#renewed$/);
    clipboard.copy.and.returnValue(false);
    fixture.componentInstance.copyLink();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.link.copyFailed');
  });

  it('offers the invoice of a saved month with days worked, unless it was rejected', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const invoiceButton = (overrides: Partial<ActivityReport>) => {
      TestBed.resetTestingModule();
      return create(mission, report(overrides)).nativeElement.querySelector('[data-invoice-report]');
    };

    expect(invoiceButton({ id: 7, status: 'DRAFT', workedDays: 3 })).not.toBeNull();
    expect(invoiceButton({ id: 7, status: 'SUBMITTED', workedDays: 3 })).not.toBeNull();
    expect(invoiceButton({ id: 7, status: 'REJECTED', workedDays: 3 })).toBeNull();
    expect(invoiceButton({ id: 7, status: 'DRAFT', workedDays: 0 })).toBeNull();
    expect(invoiceButton({ status: 'DRAFT', workedDays: 3 })).toBeNull();
  });

  it('drafts the invoice of a month and opens the invoices, or says it is invoiced already', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create(mission, report({ id: 7, status: 'VALIDATED', workedDays: 15, validatorName: 'Jean Dupont',
      decidedAt: '2026-12-01T09:42:00Z' }));
    invoices.fromReport.and.returnValue(of({ id: 1 } as Invoice));
    button(fixture, 'Créer la facture').click();
    expect(invoices.fromReport).toHaveBeenCalledWith(42, 2026, 11);
    expect(notifications.success).toHaveBeenCalledWith('activityReports.invoiced');
    expect(navigate).toHaveBeenCalledWith(['/invoices']);

    navigate.calls.reset();
    invoices.fromReport.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: { message: 'This activity report is already invoiced' } })));
    fixture.componentInstance.invoice();
    expect(notifications.error).toHaveBeenCalledWith('invoices.errors.alreadyInvoiced');
    expect(navigate).toHaveBeenCalledWith(['/invoices']);

    navigate.calls.reset();
    invoices.fromReport.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture.componentInstance.invoice();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.invoiceError');
    expect(navigate).not.toHaveBeenCalled();
    expect(fixture.componentInstance.isBusy).toBeFalse();
  });

  it('shows who approved a month, keeps it read-only, and reopens it after a warning', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const validated = report({
      id: 7, status: 'VALIDATED', validatorName: 'Jean Dupont', decidedAt: '2026-12-01T09:42:00Z',
      decisionComment: 'Conforme', comment: 'Formation le 4'
    });
    const fixture = create(mission, validated);
    service.reopen.and.returnValue(of(report({ id: 7 })));

    expect(text(fixture)).toContain('Validé par Jean Dupont le 1 déc. 2026');
    expect(text(fixture)).toContain('Commentaire : Conforme');
    expect(text(fixture)).toContain('Commentaire : Formation le 4');
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
    button(fixture, 'Rouvrir pour modifier').click();

    expect(confirmDialog.confirm).toHaveBeenCalledWith(jasmine.objectContaining({
      messageKey: 'activityReports.reopenValidatedConfirm', messageParams: { name: 'Jean Dupont' }, danger: true
    }));
    expect(service.reopen).toHaveBeenCalledWith(42, 2026, 11);
    expect(fixture.componentInstance.report?.status).toBe('DRAFT');
    expect(notifications.success).toHaveBeenCalledWith('activityReports.reopened', 2500);
  });

  it('keeps the report when reopening is cancelled or fails', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create(mission, report({ id: 7, status: 'SUBMITTED' }));
    confirmDialog.confirm.and.returnValue(of(false));
    fixture.componentInstance.reopen();
    expect(service.reopen).not.toHaveBeenCalled();

    confirmDialog.confirm.and.returnValue(of(true));
    service.reopen.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.reopen();
    expect(confirmDialog.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageKey: 'activityReports.reopenConfirm' }));
    expect(notifications.error).toHaveBeenCalledWith('activityReports.reopenError');
  });

  it('shows the rejection reason on a month that stays editable', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create(mission, report({
      id: 7, status: 'REJECTED', validatorName: 'Jean Dupont', decidedAt: '2026-12-01T09:42:00Z', decisionComment: 'Le 11 est férié'
    }));

    expect(text(fixture)).toContain('Refusé par Jean Dupont');
    expect(text(fixture)).toContain('Motif : Le 11 est férié');
    expect(fixture.componentInstance.editable).toBeTrue();
  });

  it('moves between months, asking before dropping unsaved changes', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();
    service.getReports.and.returnValue(of([report({ id: 3, month: 10, status: 'VALIDATED' })]));
    const component = fixture.componentInstance;

    component.changeMonth(1);
    expect(service.getReport).toHaveBeenCalledWith(42, 2026, 12);
    component.onDayToggled('2026-12-01');
    confirmDialog.confirm.and.returnValue(of(false));
    component.changeMonth(1);
    expect(component.month).toBe(12);
    expect(confirmDialog.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageKey: 'activityReports.discardConfirm' }));

    confirmDialog.confirm.and.returnValue(of(true));
    component.changeMonth(1);
    expect(service.getReport).toHaveBeenCalledWith(42, 2027, 1);
    component.openMonth(2027, 1);
    expect(service.getReport).toHaveBeenCalledTimes(3);
  });

  it('jumps to a saved month from its chip', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();
    fixture.componentInstance.savedReports = [report({ id: 3, month: 10, status: 'VALIDATED' })];

    button(fixture, 'Octobre 2026').click();

    expect(service.getReport).toHaveBeenCalledWith(42, 2026, 10);
  });

  it('downloads the month as PDF and reports a failure', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create(mission, report({ id: 7 }));
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    service.downloadPdf.and.returnValue(of(new Blob(['%PDF'])));

    button(fixture, 'Télécharger le PDF').click();
    expect(service.downloadPdf).toHaveBeenCalledWith(42, 2026, 11);
    expect(click).toHaveBeenCalled();

    service.downloadPdf.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.downloadPdf();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.pdfError');
  });

  it('reports a month that cannot be loaded', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();
    service.getReport.and.returnValue(throwError(() => new Error('down')));
    service.getReports.and.returnValue(throwError(() => new Error('down')));

    fixture.componentInstance.changeMonth(-1);

    expect(fixture.componentInstance.report).toBeUndefined();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.loadError');
  });

  it('handles a mission without dates, rate or ESN and ignores updates of the same mission', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const bare = { id: 42, role: 'Dev', status: ProjectStatus.WON, clientName: 'Acme' } as ProjectDto;
    const fixture = create(bare, report({ dailyRate: undefined, missionStart: undefined, missionEnd: undefined }));

    expect(service.getReport).toHaveBeenCalledWith(42, 2026, 11);
    expect(fixture.componentInstance.liveAmount).toBeUndefined();
    expect(fixture.componentInstance.templateOwner).toBe('Acme');
    expect(fixture.nativeElement.querySelector('[data-total="amount"]')).toBeNull();
    fixture.componentRef.setInput('project', { ...bare, role: 'Dev 2' });
    fixture.detectChanges();
    expect(service.getReport).toHaveBeenCalledTimes(1);
  });

  it('does nothing before a month is loaded and reports failed renewals and listings', () => {
    jasmine.clock().mockDate(new Date(2026, 10, 15));
    const fixture = create();
    const component = fixture.componentInstance;
    component.report = undefined;

    component.fill();
    component.reopen();
    expect(component.days).toEqual([]);
    expect(confirmDialog.confirm).not.toHaveBeenCalled();

    service.renewLink.and.returnValue(throwError(() => new Error('down')));
    component.renewLink();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.link.renewError');

    component.savedReports = [report({ id: 3 })];
    service.getReports.and.returnValue(throwError(() => new Error('down')));
    service.save.and.returnValue(of(report({ id: 7 })));
    component.onDayToggled('2026-11-02');
    component.save();
    expect(component.savedReports).toEqual([]);
  });
});
