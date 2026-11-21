import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { ModerationQueue, NetworkReport, ReportsDashboard } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { AdminModerationComponent } from './admin-moderation.component';
import en from '../../../assets/i18n/en.json';

describe('AdminModerationComponent', () => {
  let admin: jasmine.SpyObj<AdminService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const report = (id: number, changes: Partial<NetworkReport> = {}): NetworkReport => ({ id, targetType: 'POST', targetId: 40,
    target: { accountId: 1, handle: 'camille', name: 'Camille Durand' }, excerpt: '<b>Achetez</b> mes formations', reason: 'SPAM',
    details: 'Publicité', reporter: { accountId: 2, handle: 'leo_m', name: 'Léo Martin' }, createdAt: '2026-11-14T10:00:00',
    openOnTarget: 2, outcome: null, resolvedBy: null, resolvedAt: null, note: null, ...changes });
  const queue: ModerationQueue = { open: [report(1), report(2, { targetType: 'MEMBER', targetId: 1, excerpt: null, details: null,
    openOnTarget: 1 })], resolved: [report(3, { outcome: 'REMOVED', resolvedBy: { accountId: 9, handle: null, name: 'Chief Admin' },
    resolvedAt: '2026-11-14T11:00:00Z', note: 'Spam commercial' })], openCount: 2,
    held: [{ id: 5, author: { accountId: 3, handle: 'leo_m', name: 'Léo' }, postId: 40, text: '<i>Voir</i> https://a.example',
      reasons: ['LINKS', 'NEW_MEMBER_LINK'], createdAt: '2026-11-14T12:00:00' }] };

  let dashboard: Observable<ReportsDashboard> = of({ days: 90, total: 0, open: 0, byReason: {}, byTarget: {}, openUnderADay: 0,
    openUnderAWeek: 0, openOlder: 0, companies: [] });

  function create(read = of(queue)): ComponentFixture<AdminModerationComponent> {
    admin = jasmine.createSpyObj('AdminService', ['reports', 'decide', 'release', 'releaseReview', 'reportsDashboard']);
    admin.reports.and.returnValue(read);
    admin.reportsDashboard.and.returnValue(dashboard);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [AdminModerationComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AdminService, useValue: admin },
        { provide: NotificationService, useValue: notifications }, { provide: ConfirmDialogService, useValue: confirm }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(AdminModerationComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the open reports with the text as text, and the latest decisions', () => {
    const element: HTMLElement = create().nativeElement;
    expect(element.textContent).toContain('Open reports (2)');
    const first = element.querySelector('[data-report="1"]')!;
    expect(first.querySelector('[data-report-excerpt] b')).toBeNull();
    expect(first.querySelector('[data-report-excerpt]')?.textContent).toContain('<b>Achetez</b>');
    expect(first.querySelector('[data-report-count]')?.textContent).toContain('2 reports');
    expect(first.textContent).toContain('@camille');
    expect(first.textContent).toContain('Spam or advertising');
    expect(first.querySelector('[data-decide="REMOVED"]')).not.toBeNull();
    const member = element.querySelector('[data-report="2"]')!;
    expect(member.querySelector('[data-decide="REMOVED"]')).toBeNull();
    expect(member.querySelector('[data-report-count]')).toBeNull();
    expect(element.querySelector('[data-resolved="3"]')?.textContent).toContain('Chief Admin');
    expect(element.querySelector('[data-resolved="3"]')?.textContent).toContain('Spam commercial');
  });

  it('decides with the note and the days, a ban once confirmed', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const decided: ModerationQueue = { open: [], resolved: [], openCount: 0, held: [] };
    admin.decide.and.returnValue(of(decided));
    component.setNote(queue.open[0], '  Spam  ');
    component.decide(queue.open[0], 'REMOVED');
    expect(admin.decide).toHaveBeenCalledWith(1, 'REMOVED', 'Spam', null);
    expect(notifications.success).toHaveBeenCalledWith('admin.moderation.decided');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-moderation-empty]')).not.toBeNull();

    component.setDays(queue.open[1], '0');
    component.decide(queue.open[1], 'SUSPENDED');
    expect(notifications.error).toHaveBeenCalledWith('admin.moderation.daysInvalid');
    component.setDays(queue.open[1], '14');
    component.decide(queue.open[1], 'SUSPENDED');
    expect(admin.decide).toHaveBeenCalledWith(2, 'SUSPENDED', null, 14);

    admin.decide.calls.reset();
    confirm.confirm.and.returnValue(of(false));
    component.decide(queue.open[1], 'BANNED');
    expect(admin.decide).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    admin.decide.and.returnValue(throwError(() => new Error('down')));
    component.decide(queue.open[1], 'BANNED');
    expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageParams: { who: '@camille' } }));
    expect(notifications.error).toHaveBeenCalledWith('admin.saveError');
    expect(component.deciding).toBeNull();
  });

  it('publishes or rejects what the spam score held, and says when it can no longer be published', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const held = element.querySelector('[data-held="5"]')!;
    expect(held.textContent).toContain('Comment');
    expect(held.textContent).toContain('many links');
    expect(held.querySelector('[data-held-text] i')).toBeNull();
    expect(held.querySelector('[data-held-text]')?.textContent).toContain('<i>Voir</i>');
    admin.release.and.returnValue(of({ ...queue, held: [] }));
    (held.querySelector('[data-release="publish"]') as HTMLButtonElement).click();
    expect(admin.release).toHaveBeenCalledWith(5, true);
    expect(notifications.success).toHaveBeenCalledWith('admin.moderation.released');
    fixture.detectChanges();
    expect(element.querySelector('[data-held="5"]')).toBeNull();
    component.release(queue.held[0], false);
    expect(admin.release).toHaveBeenCalledWith(5, false);
    expect(notifications.success).toHaveBeenCalledWith('admin.moderation.rejected');
    admin.release.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.release(queue.held[0], true);
    expect(notifications.error).toHaveBeenCalledWith('admin.moderation.stale');
    admin.release.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.release(queue.held[0], true);
    expect(notifications.error).toHaveBeenCalledWith('admin.saveError');
    expect(component.releasing).toBeNull();
  });

  it('publishes a held review edited out, or rejects it', () => {
    const review = { id: 9, author: { accountId: 4, handle: 'bob', name: 'Bob Martin' }, companyName: 'Capgemini', kind: 'ESN' as const,
      paymentRating: 2, processRating: 4, rateRating: 3, text: 'Call Jean Dupont at 06 12 34 56 78.', reasons: ['PHONE', 'CONTACT_NAME'],
      createdAt: '2026-11-21T10:00:00' };
    const fixture = create(of({ ...queue, heldReviews: [review] }));
    const component = fixture.componentInstance;
    const card: HTMLElement = fixture.nativeElement.querySelector('[data-held-review="9"]');
    expect(card.textContent).toContain('Review of Capgemini');
    expect(card.textContent).toContain('Phone number');
    expect(component.reviewText(review)).toBe(review.text);
    component.editReview(review, ' Slow to answer. ');
    admin.releaseReview.and.returnValue(of({ ...queue, heldReviews: [] }));
    (card.querySelector('[data-release-review="publish"]') as HTMLButtonElement).click();
    expect(admin.releaseReview).toHaveBeenCalledWith(9, true, 'Slow to answer.');
    expect(notifications.success).toHaveBeenCalledWith('admin.moderation.released');
    component.releaseReview(review, false);
    expect(admin.releaseReview).toHaveBeenCalledWith(9, false, null);
    admin.releaseReview.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    component.releaseReview(review, true);
    expect(notifications.error).toHaveBeenCalledWith('admin.moderation.stale');
    expect(component.releasingReview).toBeNull();
  });

  it('shows the reports at a glance, by reason, target, age and company, and goes on without it', () => {
    dashboard = of({ days: 90, total: 3, open: 2, byReason: { SPAM: 1, MISLEADING: 2 }, byTarget: { REVIEW: 2, POST: 1 }, openUnderADay: 1,
      openUnderAWeek: 1, openOlder: 0, companies: [{ companyName: 'Capgemini', open: 1, total: 2 }] });
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-dashboard-open]')?.textContent?.trim()).toBe('2');
    expect(fixture.componentInstance.counts({ SPAM: 1, MISLEADING: 2 }).map(entry => entry.key)).toEqual(['MISLEADING', 'SPAM']);
    expect(element.querySelector('[data-dashboard-companies]')?.textContent).toContain('Capgemini');
    TestBed.resetTestingModule();
    dashboard = throwError(() => new Error('down'));
    const without = create();
    expect(without.componentInstance.dashboard).toBeNull();
    expect(without.nativeElement.querySelector('[data-reports-dashboard]')).toBeNull();
    dashboard = of({ days: 90, total: 0, open: 0, byReason: {}, byTarget: {}, openUnderADay: 0, openUnderAWeek: 0, openOlder: 0, companies: [] });
  });

  it('names who is gone by their account, and says when the queue cannot be loaded', () => {
    const component = create().componentInstance;
    expect(component.who({ accountId: 5, handle: null, name: null })).toBe('#5');
    expect(component.who(null)).toBe('');
    expect(component.daysOf(queue.open[0])).toBe(7);
    TestBed.resetTestingModule();
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('admin.loadError');
  });
});
