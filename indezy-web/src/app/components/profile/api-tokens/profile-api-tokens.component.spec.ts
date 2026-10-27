import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { ApiToken } from '../../../models';
import { AccountSecurityService } from '../../../services/account-security/account-security.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ProfileApiTokensComponent } from './profile-api-tokens.component';
import en from '../../../../assets/i18n/en.json';

describe('ProfileApiTokensComponent', () => {
  let security: jasmine.SpyObj<AccountSecurityService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  const zapier: ApiToken = { id: 1, name: 'Zapier', tokenStart: 'idz_AbCdEfGh', createdAt: '2026-10-01T08:00:00',
    lastUsedAt: '2026-10-26T08:00:00', expiresAt: '2026-12-30T08:00:00' };
  const script: ApiToken = { ...zapier, id: 2, name: 'Backup script', lastUsedAt: null, expiresAt: null };

  function create(tokens: ApiToken[] = [zapier]): ComponentFixture<ProfileApiTokensComponent> {
    security.apiTokens.and.returnValue(of(tokens));
    TestBed.configureTestingModule({
      imports: [ProfileApiTokensComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: AccountSecurityService, useValue: security },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: Clipboard, useValue: clipboard }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfileApiTokensComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ProfileApiTokensComponent>, selector: string): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  beforeEach(() => {
    security = jasmine.createSpyObj('AccountSecurityService', ['apiTokens', 'createApiToken', 'revokeApiToken']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
  });

  it('lists the tokens by name and start, with their use and expiry', () => {
    const fixture = create([zapier, script]);

    expect(text(fixture, '[data-token="1"]')).toContain('Zapier idz_AbCdEfGh…');
    expect(text(fixture, '[data-token="1"]')).toContain('last used');
    expect(text(fixture, '[data-token="2"]')).toContain('never used');
    expect(text(fixture, '[data-token="2"]')).toContain('does not expire');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-token="2"] button')?.getAttribute('aria-label'))
      .toBe('Revoke Backup script');
  });

  it('creates a token, shows it once with how to use it, and copies it', () => {
    const fixture = create([]);
    const component = fixture.componentInstance;
    security.createApiToken.and.returnValue(of({ token: script, secret: 'idz_secret-value' }));

    component.create();
    expect(security.createApiToken).not.toHaveBeenCalled();

    component.form.setValue({ name: ' Backup script ', expiresInDays: 0 });
    component.create();

    expect(security.createApiToken).toHaveBeenCalledWith('Backup script', null);
    expect(text(fixture, '[data-token-secret]')).toBe('idz_secret-value');
    expect(text(fixture, '[data-created-token]')).toContain('curl -H "Authorization: Bearer idz_AbCdEfGh…"');
    expect(component.form.getRawValue()).toEqual({ name: '', expiresInDays: 90 });

    clipboard.copy.and.returnValue(true);
    component.copy();
    expect(clipboard.copy).toHaveBeenCalledWith('idz_secret-value');
    expect(notifications.success).toHaveBeenCalledWith('profile.apiTokens.copied');

    component.done();
    expect(text(fixture, '[data-token-secret]')).toBe('');
    component.copy();
    expect(clipboard.copy).toHaveBeenCalledTimes(1);
  });

  it('says when a token cannot be created, and stops at ten', () => {
    const fixture = create([]);
    security.createApiToken.and.returnValue(throwError(() => new Error('409')));
    fixture.componentInstance.form.setValue({ name: 'Zapier', expiresInDays: 30 });
    fixture.componentInstance.create();
    expect(security.createApiToken).toHaveBeenCalledWith('Zapier', 30);
    expect(fixture.componentInstance.creating).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('profile.apiTokens.createError');

    fixture.componentInstance.tokens = Array.from({ length: 10 }, (_, id) => ({ ...zapier, id }));
    expect(text(fixture, '[data-tokens-full]')).toContain('You have 10 tokens');
  });

  it('revokes a token once confirmed', () => {
    const fixture = create([zapier, script]);
    const component = fixture.componentInstance;
    confirm.confirm.and.returnValue(of(false));
    component.revoke(zapier);
    expect(security.revokeApiToken).not.toHaveBeenCalled();

    confirm.confirm.and.returnValue(of(true));
    security.revokeApiToken.and.returnValue(of(undefined));
    component.created = { token: zapier, secret: 'idz_x' };
    component.revoke(zapier);
    expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageParams: { name: 'Zapier' }, danger: true }));
    expect(component.tokens).toEqual([script]);
    expect(component.created).toBeUndefined();
    expect(notifications.success).toHaveBeenCalledWith('profile.apiTokens.revoked');

    security.revokeApiToken.and.returnValue(throwError(() => new Error('404')));
    component.revoke(script);
    expect(notifications.error).toHaveBeenCalledWith('profile.apiTokens.revokeError');
  });

  it('says when the tokens cannot be read', () => {
    security.apiTokens.and.returnValue(throwError(() => new Error('500')));
    TestBed.configureTestingModule({
      imports: [ProfileApiTokensComponent, TranslateModule.forRoot()],
      providers: [
        { provide: AccountSecurityService, useValue: security },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: Clipboard, useValue: clipboard }
      ]
    });
    TestBed.createComponent(ProfileApiTokensComponent).detectChanges();
    expect(notifications.error).toHaveBeenCalledWith('profile.apiTokens.loadError');
  });
});
