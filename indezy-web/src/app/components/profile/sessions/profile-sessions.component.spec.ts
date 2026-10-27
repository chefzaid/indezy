import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AccountEvent, AccountSession } from '../../../models';
import { AccountSecurityService } from '../../../services/account-security/account-security.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ProfileSessionsComponent } from './profile-sessions.component';
import en from '../../../../assets/i18n/en.json';

describe('ProfileSessionsComponent', () => {
  let security: jasmine.SpyObj<AccountSecurityService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const laptop: AccountSession = { id: 1, method: 'PASSWORD', device: 'Firefox on Windows', createdAt: '2026-10-20T08:00:00',
    lastSeenAt: '2026-10-27T09:00:00', expiresAt: '2026-10-28T08:00:00', current: true };
  const phone: AccountSession = { ...laptop, id: 2, method: 'KEYCLOAK', device: 'Chrome on Android', current: false, lastSeenAt: null };
  const unknown: AccountSession = { ...laptop, id: 3, device: null, current: false };
  const event = (id: number, type: AccountEvent['type'], detail: string | null = null): AccountEvent =>
    ({ id, type, detail, device: 'Firefox on Windows', occurredAt: '2026-10-27T09:00:00' });

  function create(): ComponentFixture<ProfileSessionsComponent> {
    TestBed.configureTestingModule({
      imports: [ProfileSessionsComponent, TranslateModule.forRoot()],
      providers: [
        { provide: AccountSecurityService, useValue: security },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfileSessionsComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ProfileSessionsComponent>, selector: string): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  beforeEach(() => {
    security = jasmine.createSpyObj('AccountSecurityService', ['sessions', 'end', 'endOthers', 'activity']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    security.sessions.and.returnValue(of([laptop, phone, unknown]));
    security.activity.and.returnValue(of({ events: [event(5, 'SIGNED_IN', 'KEYCLOAK'), event(4, 'SIGN_IN_REFUSED', 'TWO_FACTOR')],
      total: 3, page: 0, size: 20 }));
  });

  it('lists where the account is signed in, this browser marked and without a sign-out button', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;

    expect(text(fixture, '[data-session="1"]')).toContain('Firefox on Windows This browser');
    expect(element.querySelector('[data-session="1"] button')).toBeNull();
    expect(text(fixture, '[data-session="2"]')).toContain('Swirlit single sign-on');
    expect(element.querySelector('[data-session="2"] button')?.getAttribute('aria-label')).toBe('Sign out Chrome on Android');
    expect(text(fixture, '[data-session="3"]')).toContain('Unknown browser');
    expect(fixture.componentInstance.icon(phone)).toBe('smartphone');
    expect(fixture.componentInstance.icon(laptop)).toBe('computer');
  });

  it('signs one session out', () => {
    const fixture = create();
    security.end.and.returnValue(of(undefined));

    fixture.componentInstance.end(phone);

    expect(security.end).toHaveBeenCalledWith(2);
    expect(fixture.componentInstance.sessions.map(session => session.id)).toEqual([1, 3]);
    expect(notifications.success).toHaveBeenCalledWith('profile.sessions.ended');
    security.end.and.returnValue(throwError(() => new Error('404')));
    fixture.componentInstance.end(unknown);
    expect(notifications.error).toHaveBeenCalledWith('profile.sessions.endError');
  });

  it('signs out everywhere else once confirmed', () => {
    const fixture = create();
    confirm.confirm.and.returnValue(of(false));
    fixture.componentInstance.endOthers();
    expect(security.endOthers).not.toHaveBeenCalled();

    confirm.confirm.and.returnValue(of(true));
    security.endOthers.and.returnValue(of({ count: 2 }));
    fixture.componentInstance.endOthers();

    expect(fixture.componentInstance.sessions).toEqual([laptop]);
    expect(notifications.success).toHaveBeenCalledWith('profile.sessions.endedOthers', undefined, { count: 2 });
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-end-others]')).toBeDefined();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-end-others]')).toBeNull();

    security.endOthers.and.returnValue(throwError(() => new Error('500')));
    fixture.componentInstance.endOthers();
    expect(notifications.error).toHaveBeenCalledWith('profile.sessions.endError');
  });

  it('describes the activity in words, and reads more of it', () => {
    const fixture = create();
    expect(text(fixture, '[data-event="SIGNED_IN"]')).toContain('Signed in (Swirlit single sign-on)');
    expect(text(fixture, '[data-event="SIGN_IN_REFUSED"]')).toContain('Sign-in refused: wrong authenticator code');

    security.activity.and.returnValue(of({ events: [event(3, 'OTHER_SESSIONS_REVOKED', '2')], total: 3, page: 1, size: 20 }));
    fixture.componentInstance.more();
    expect(security.activity).toHaveBeenCalledWith(1);
    expect(text(fixture, '[data-event="OTHER_SESSIONS_REVOKED"]')).toContain('Signed out of 2 other sessions');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-more-activity]')).toBeNull();

    const component = fixture.componentInstance;
    expect(component.describe(event(9, 'SESSION_REVOKED', null))).toBe('Signed out Unknown browser remotely');
    expect(component.describe(event(9, 'SIGN_IN_REFUSED', 'PASSWORD'))).toBe('Sign-in refused: wrong password');
    expect(component.eventIcon(event(9, 'PASSWORD_CHANGED'))).toBe('password');
    expect(component.eventIcon({ ...event(9, 'PASSWORD_CHANGED'), type: 'SOMETHING_NEW' as never })).toBe('history');
  });

  it('says when nothing is recorded, or when it cannot be read', () => {
    security.activity.and.returnValue(of({ events: [], total: 0, page: 0, size: 20 }));
    const fixture = create();
    expect(text(fixture, '[data-activity]')).toContain('Nothing recorded yet.');

    security.activity.and.returnValue(throwError(() => new Error('500')));
    fixture.componentInstance.more();
    expect(fixture.componentInstance.loadingMore).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('profile.sessions.loadError');
  });

  it('says when the sessions cannot be read', () => {
    security.sessions.and.returnValue(throwError(() => new Error('500')));
    security.activity.and.returnValue(throwError(() => new Error('500')));
    create();
    expect(notifications.error).toHaveBeenCalledTimes(2);
  });
});
