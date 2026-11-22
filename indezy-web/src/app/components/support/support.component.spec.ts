import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { SupportRequest } from '../../models';
import { NotificationService } from '../../services/notification/notification.service';
import { SupportService } from '../../services/support/support.service';
import { SupportComponent } from './support.component';
import { version as APP_VERSION } from '../../../../package.json';

describe('SupportComponent', () => {
  let support: jasmine.SpyObj<SupportService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const answered: SupportRequest = { id: 1, kind: 'PROBLEM', subject: 'PDF empty', description: 'Blank.', context: null, status: 'CLOSED',
    answer: 'Fixed.', createdAt: '2026-11-22T10:00:00', answeredAt: '2026-11-22T11:00:00Z' };

  function create(params: Record<string, string> = {}) {
    support = jasmine.createSpyObj('SupportService', ['send', 'mine']);
    support.mine.and.returnValue(of([answered]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [SupportComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: SupportService, useValue: support }, { provide: NotificationService, useValue: notifications },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(params) } } }]
    });
    const fixture = TestBed.createComponent(SupportComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the requests with their answers and sends a new one with its context', () => {
    const fixture = create({ kind: 'QUESTION', page: '/invoices', error: 'Server error' });
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-support-answer]')?.textContent).toContain('Fixed.');
    const component = fixture.componentInstance;
    expect(component.form.value.kind).toBe('QUESTION');
    expect(component.context).toContain('/invoices');
    expect(component.context).toContain('Server error');
    expect(component.context).toContain(`support.context.version: ${APP_VERSION}`);
    component.send();
    expect(support.send).not.toHaveBeenCalled();

    support.send.and.returnValue(of({ ...answered, id: 2, status: 'OPEN', answer: null }));
    component.form.patchValue({ subject: ' Une question ', description: ' Comment ? ' });
    component.send();
    const sent = support.send.calls.mostRecent().args[0];
    expect(sent.subject).toBe('Une question');
    expect(sent.context).toContain('/invoices');
    expect(component.requests.length).toBe(2);
    expect(notifications.success).toHaveBeenCalledWith('support.sent');

    component.form.patchValue({ subject: 'Sans contexte', description: 'x', sendContext: false });
    component.send();
    expect(support.send.calls.mostRecent().args[0].context).toBeNull();
  });

  it('says why a request was not sent or the requests not loaded', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.form.patchValue({ subject: 'S', description: 'D' });
    support.send.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('support.dailyLimit');
    support.send.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('support.sendError');
    TestBed.resetTestingModule();
    support = jasmine.createSpyObj('SupportService', ['send', 'mine']);
    support.mine.and.returnValue(throwError(() => new Error('down')));
    TestBed.configureTestingModule({
      imports: [SupportComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: SupportService, useValue: support }, { provide: NotificationService, useValue: notifications }]
    });
    TestBed.createComponent(SupportComponent).detectChanges();
    expect(notifications.error).toHaveBeenCalledWith('support.loadError');
  });
});
