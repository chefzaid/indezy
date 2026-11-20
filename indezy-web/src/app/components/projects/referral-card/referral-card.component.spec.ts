import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { ProjectDto } from '../../../models';
import { ProjectReferralView } from '../../../models/project.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectReferralService } from '../../../services/project/project-referral.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import en from '../../../../assets/i18n/en.json';
import { ReferralCardComponent, referrerFromSource } from './referral-card.component';

describe('ReferralCardComponent', () => {
  let referrals: jasmine.SpyObj<ProjectReferralService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const project = { id: 7, role: 'Lead Kafka', dailyRate: 600, sourceName: 'Indezy @leo_m' } as ProjectDto;
  const view = (changes: Partial<ProjectReferralView['referral']> = {}, bonus: number | null = 50): ProjectReferralView => ({ projectId: 7,
    role: 'Lead Kafka', clientName: 'Acme', workedDays: 2.5, earned: 1500, bonus,
    referral: { referrer: '@leo_m', direction: 'I_PAY', bonusKind: 'PER_DAY', bonusAmount: 20, status: 'DUE', paidOn: null, note: null, ...changes } });

  function create(found: ProjectReferralView | null = null) {
    referrals = jasmine.createSpyObj('ProjectReferralService', ['get', 'save', 'remove']);
    referrals.get.and.returnValue(of(found));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({ imports: [ReferralCardComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: ProjectReferralService, useValue: referrals },
        { provide: NotificationService, useValue: notifications }, { provide: ConfirmDialogService, useValue: confirm }] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ReferralCardComponent);
    fixture.componentRef.setInput('project', project);
    fixture.detectChanges();
    return fixture;
  }

  it('reads the member who referred an opportunity from its source', () => {
    expect(referrerFromSource('Indezy @leo_m')).toBe('@leo_m');
    expect(referrerFromSource('LinkedIn')).toBe('');
    expect(referrerFromSource(null)).toBe('');
  });

  it('notes a co-optation, the referrer taken from the source, with a bonus per day', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(referrals.get).toHaveBeenCalledWith(7);
    (element.querySelector('[data-referral-add]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(component.form.controls.referrer.value).toBe('@leo_m');
    component.form.patchValue({ bonusKind: 'PER_DAY' });
    expect(component.amountInvalid).toBeTrue();
    component.save();
    expect(referrals.save).not.toHaveBeenCalled();
    component.form.patchValue({ bonusAmount: 20, status: 'DUE', paidOn: '2026-12-01', note: ' ' });
    referrals.save.and.returnValue(of(view()));
    component.save();
    fixture.detectChanges();
    expect(referrals.save).toHaveBeenCalledWith(7, { referrer: '@leo_m', direction: 'I_PAY', bonusKind: 'PER_DAY', bonusAmount: 20,
      status: 'DUE', paidOn: null, note: null });
    expect(element.querySelector('[data-referral-who]')?.textContent).toContain('Referred by @leo_m');
    expect(element.querySelector('[data-referral-bonus]')?.textContent).toContain('50 €');
    expect(element.querySelector('[data-referral-bonus]')?.textContent).toContain('20 € × 2.5 days worked');
    expect(notifications.success).toHaveBeenCalledWith('referral.saved');
    referrals.save.and.returnValue(throwError(() => new Error('down')));
    component.edit();
    component.save();
    expect(notifications.error).toHaveBeenCalledWith('referral.saveError');
  });

  it('shows a paid share, drops the amount with the bonus, and removes the co-optation once confirmed', () => {
    const fixture = create(view({ direction: 'I_RECEIVE', bonusKind: 'PERCENT', bonusAmount: 5, status: 'PAID', paidOn: '2026-12-05' }, 75));
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-referral-who]')?.textContent).toContain('You referred @leo_m');
    expect(element.querySelector('[data-referral-state]')?.textContent).toContain('Paid');
    expect(element.querySelector('[data-referral-bonus]')?.textContent).toContain('5% of 1,500 € earned');
    const component = fixture.componentInstance;
    component.edit();
    component.form.controls.bonusKind.setValue(null);
    component.kindChanged();
    expect(component.form.controls.bonusAmount.value).toBeNull();
    expect(component.amountInvalid).toBeFalse();
    component.editing = false;
    confirm.confirm.and.returnValue(of(true));
    referrals.remove.and.returnValue(of(undefined));
    component.remove();
    fixture.detectChanges();
    expect(referrals.remove).toHaveBeenCalledWith(7);
    expect(element.querySelector('[data-referral-add]')).not.toBeNull();
  });
});

describe('ProjectReferralService', () => {
  it('reads, saves and removes a mission\'s co-optation', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ProjectReferralService);
    const http = TestBed.inject(HttpTestingController);
    service.get(7).subscribe();
    http.expectOne(call => call.method === 'GET' && call.url.endsWith('/projects/7/referral'));
    service.save(7, { referrer: '@leo_m', direction: 'I_PAY', bonusKind: null, bonusAmount: null, status: 'PROMISED', paidOn: null, note: null })
      .subscribe();
    http.expectOne(call => call.method === 'PUT' && call.url.endsWith('/projects/7/referral'));
    service.remove(7).subscribe();
    http.expectOne(call => call.method === 'DELETE' && call.url.endsWith('/projects/7/referral'));
    http.verify();
  });
});
