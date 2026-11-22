import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { NotificationService } from '../../services/notification/notification.service';
import { SupportService } from '../../services/support/support.service';
import { AdminSupportComponent } from './admin-support.component';

describe('AdminSupportComponent', () => {
  const open = { request: { id: 3, kind: 'PROBLEM' as const, subject: 'PDF empty', description: 'Blank.', context: 'Page: /invoices',
    status: 'OPEN' as const, answer: null, createdAt: '2026-11-22T10:00:00', answeredAt: null }, userId: 7, email: 'alice@example.com' };

  function create(loaded = of([open])) {
    const support = jasmine.createSpyObj('SupportService', ['received', 'answer']);
    support.received.and.returnValue(loaded);
    const notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [AdminSupportComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: SupportService, useValue: support },
        { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(AdminSupportComponent);
    fixture.detectChanges();
    return { fixture, support, notifications };
  }

  it('lists the requests and answers one, closing it', () => {
    const { fixture, support, notifications } = create();
    expect(fixture.nativeElement.textContent).toContain('alice@example.com');
    const component = fixture.componentInstance;
    component.answer(component.requests[0], true);
    expect(support.answer).not.toHaveBeenCalled();
    support.answer.and.returnValue(of({ ...open.request, status: 'CLOSED', answer: 'Fixed.' }));
    component.drafts[3] = ' Fixed. ';
    component.answer(component.requests[0], true);
    expect(support.answer).toHaveBeenCalledWith(3, 'Fixed.', true);
    expect(component.requests[0].request.status).toBe('CLOSED');
    expect(notifications.success).toHaveBeenCalledWith('admin.support.answered');
  });

  it('says when the requests cannot be loaded or answered', () => {
    const { fixture, support, notifications } = create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('admin.support.loadError');
    support.answer.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.drafts[3] = 'x';
    fixture.componentInstance.answer(open, false);
    expect(notifications.error).toHaveBeenCalledWith('admin.support.answerError');
  });
});
