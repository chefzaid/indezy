import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import fr from '../../../assets/i18n/fr.json';
import { ActivityReport, PublicActivityReport } from '../../models';
import { ActivityReportService } from '../../services/activity-report/activity-report.service';
import { NotificationService } from '../../services/notification/notification.service';
import { LANGUAGE_STORAGE_KEY } from '../../shared/locale/app-locale';
import { ActivityReportValidationComponent } from './activity-report-validation.component';

describe('ActivityReportValidationComponent', () => {
  let service: jasmine.SpyObj<ActivityReportService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function view(overrides: Partial<ActivityReport> = {}, showsLocation = true): PublicActivityReport {
    return {
      consultantName: 'Zoé Martin', clientName: 'Société Générale', middlemanName: 'Capgemini',
      missionRole: 'Développeur Java', showsLocation,
      report: {
        year: 2026, month: 11, status: 'SUBMITTED', comment: 'Pont du 10 posé',
        days: [{ date: '2026-11-02', type: 'ONSITE', halfDay: false }, { date: '2026-11-03', type: 'REMOTE', halfDay: true }],
        publicHolidays: [{ date: '2026-11-11', holiday: 'ARMISTICE' }], businessDays: 20,
        workedDays: 1.5, onsiteDays: 1, remoteDays: 0.5, absenceDays: 0, ...overrides
      }
    };
  }

  function create(fragment: string | null, loaded?: PublicActivityReport | Error): ComponentFixture<ActivityReportValidationComponent> {
    registerLocaleData(localeFr);
    service = jasmine.createSpyObj('ActivityReportService', ['getPublicReport', 'downloadPublicPdf', 'decide']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    service.getPublicReport.and.returnValue(loaded instanceof Error ? throwError(() => loaded) : of(loaded ?? view()));
    TestBed.configureTestingModule({
      imports: [ActivityReportValidationComponent, TranslateModule.forRoot()],
      providers: [
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: ActivatedRoute, useValue: { snapshot: { fragment } } },
        { provide: ActivityReportService, useValue: service },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(ActivityReportValidationComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ActivityReportValidationComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).innerText.replace(/\s+/g, ' ');
  }

  function fillName(fixture: ComponentFixture<ActivityReportValidationComponent>, name: string, comment = ''): void {
    fixture.componentInstance.form.setValue({ validatorName: name, comment });
  }

  it('reads the token after the # and shows the month to validate', () => {
    const fixture = create('link-token');

    expect(service.getPublicReport).toHaveBeenCalledWith('link-token');
    expect(text(fixture)).toContain('Validation du compte rendu d\'activité');
    expect(text(fixture)).toContain('Zoé Martin vous demande de valider son compte rendu d\'activité de Novembre 2026.');
    expect(text(fixture)).toContain('ESN / intermédiaire Capgemini');
    expect(text(fixture)).toContain('Jours travaillés 1,5');
    expect(text(fixture)).toContain('Télétravail 0,5');
    expect(text(fixture)).toContain('Pont du 10 posé');
    expect(fixture.nativeElement.querySelectorAll('app-activity-calendar .day[data-date]').length).toBe(30);
    expect(fixture.nativeElement.querySelector('app-activity-calendar button')).toBeNull();
  });

  it('leaves out the on-site and remote split when the template hides it', () => {
    const fixture = create('link-token', view({}, false));

    expect(text(fixture)).not.toContain('Télétravail 0,5');
  });

  it('explains an invalid link without a token or when the server refuses it', () => {
    expect(text(create(null))).toContain('Lien invalide ou expiré');
    expect(service.getPublicReport).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    expect(text(create('expired', new Error('404')))).toContain('Ce lien de validation n\'est plus valable.');
  });

  it('asks for the validator name before approving, then shows the approval', () => {
    const fixture = create('link-token');
    service.decide.and.returnValue(of(view({ status: 'VALIDATED', validatorName: 'Jean Dupont', decidedAt: '2026-12-01T09:42:00Z' })));

    fillName(fixture, '   ');
    fixture.componentInstance.approve();
    expect(service.decide).not.toHaveBeenCalled();
    expect(text(fixture)).toContain('Votre nom est requis pour signer');

    fillName(fixture, ' Jean Dupont ', ' Conforme ');
    fixture.componentInstance.approve();

    expect(service.decide).toHaveBeenCalledWith('link-token', { approved: true, validatorName: 'Jean Dupont', comment: 'Conforme' });
    expect(fixture.componentInstance.answered).toBeTrue();
    expect(text(fixture)).toContain('Compte rendu validé');
    expect(text(fixture)).toContain('Validé par Jean Dupont le 1 déc. 2026');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('needs a reason to reject', () => {
    const fixture = create('link-token');
    service.decide.and.returnValue(of(view({
      status: 'REJECTED', validatorName: 'Jean Dupont', decidedAt: '2026-12-01T09:42:00Z', decisionComment: 'Le 11 est férié'
    })));

    fillName(fixture, 'Jean Dupont');
    fixture.componentInstance.reject();
    expect(service.decide).not.toHaveBeenCalled();
    expect(text(fixture)).toContain('Indiquez le motif du refus');

    fillName(fixture, 'Jean Dupont', 'Le 11 est férié');
    fixture.componentInstance.reject();

    expect(service.decide).toHaveBeenCalledWith('link-token', { approved: false, validatorName: 'Jean Dupont', comment: 'Le 11 est férié' });
    expect(text(fixture)).toContain('Compte rendu refusé');
    expect(text(fixture)).toContain('Motif : Le 11 est férié');
  });

  it('reloads the report when someone else answered first, and reports other failures', () => {
    const fixture = create('link-token');
    fillName(fixture, 'Jean Dupont');
    service.decide.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    fixture.componentInstance.approve();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.validation.alreadyDecided', 5000);
    expect(service.getPublicReport).toHaveBeenCalledTimes(2);

    service.decide.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture.componentInstance.approve();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.validation.decisionError', 5000);
    expect(fixture.componentInstance.isSending).toBeFalse();
  });

  it('says when the report no longer waits for an answer', () => {
    const fixture = create('link-token', view({ status: 'DRAFT' }));

    expect(text(fixture)).toContain('Ce compte rendu n\'attend plus de validation.');
  });

  it('downloads the PDF through the link and reports a failure', () => {
    const fixture = create('link-token');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    service.downloadPublicPdf.and.returnValue(of(new Blob(['%PDF'])));

    fixture.componentInstance.downloadPdf();
    expect(service.downloadPublicPdf).toHaveBeenCalledWith('link-token');
    expect(click).toHaveBeenCalled();

    service.downloadPublicPdf.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.downloadPdf();
    expect(notifications.error).toHaveBeenCalledWith('activityReports.pdfError');
  });

  it('switches the language with a reload that keeps the link', () => {
    const fixture = create('link-token');
    const reload = spyOn(fixture.componentInstance, 'reloadPage');
    const store = spyOn(localStorage, 'setItem');

    fixture.componentInstance.switchLanguage(fixture.componentInstance.language);
    expect(reload).not.toHaveBeenCalled();

    fixture.componentInstance.switchLanguage(fixture.componentInstance.language === 'fr' ? 'en' : 'fr');
    expect(store).toHaveBeenCalledWith(LANGUAGE_STORAGE_KEY, jasmine.any(String));
    expect(reload).toHaveBeenCalled();
  });

  it('formats nothing before a report is loaded or decided', () => {
    const fixture = create(null);

    expect(fixture.componentInstance.monthLabel).toBe('');
    expect(fixture.componentInstance.decidedAt).toBe('');
  });
});
