import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, MemberPage, NetworkMembership } from '../../../models';
import { NetworkService, NoticeSettings } from '../../../services/network/network.service';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ProfileNetworkComponent } from './profile-network.component';
import en from '../../../../assets/i18n/en.json';

describe('ProfileNetworkComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let messagesApi: jasmine.SpyObj<NetworkMessagesService>;
  const fields = { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false };
  const card: MemberCard = { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null, headline: 'Architecte Java',
    stack: ['Java', 'Kafka'], city: 'Lyon', remote: true, availability: 'AVAILABLE', availabilityDate: '2026-12-02', languages: ['Français'],
    verified: false };
  const auto = { mode: 'AUTO' as const, date: null };
  const outside: NetworkMembership = { joined: false, handle: 'camille', fields, availability: auto, card: null, privateCard: false, esnSuggestions: false };
  const member: NetworkMembership = { joined: true, handle: 'camille', fields, availability: auto, card, privateCard: false, esnSuggestions: false };

  function create(membership = of(outside)): ComponentFixture<ProfileNetworkComponent> {
    network = jasmine.createSpyObj('NetworkService', ['membership', 'join', 'leave', 'handle', 'verify', 'restrictions', 'mute', 'block',
      'notificationSettings', 'saveNotificationSettings', 'followRequests']);
    network.followRequests.and.returnValue(of([]));
    network.notificationSettings.and.returnValue(of({ off: ['REPOST'], emailed: ['LIKE'] }));
    network.membership.and.returnValue(membership);
    network.restrictions.and.returnValue(of({ muted: [], blocked: [] }));
    network.handle.and.returnValue(of({ handle: 'x', available: true }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    messagesApi = jasmine.createSpyObj('NetworkMessagesService', ['messaging', 'saveMessaging']);
    messagesApi.messaging.and.returnValue(of({ readReceipts: true, typing: true }));
    TestBed.configureTestingModule({
      imports: [ProfileNetworkComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: NetworkService, useValue: network },
        { provide: NotificationService, useValue: notifications }, { provide: ConfirmDialogService, useValue: confirm },
        { provide: NetworkMessagesService, useValue: messagesApi }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfileNetworkComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('offers the suggested handle, then joins with the fields chosen and shows the card', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(component.form.value.handle).toBe('camille');
    expect(element.textContent).toContain('You are not a member.');
    expect(element.querySelector('[data-network-leave]')).toBeNull();

    network.join.and.returnValue(of(member));
    component.form.controls.fields.patchValue({ location: false, remote: true });
    component.save();
    expect(network.join).toHaveBeenCalledWith('camille', { ...fields, location: false, remote: true }, auto);
    expect(notifications.success).toHaveBeenCalledWith('profile.network.joined');
    fixture.detectChanges();
    expect(element.querySelector('[data-network-joined]')?.textContent).toContain('@camille');
    expect(element.querySelector('[data-network-page]')?.getAttribute('href')).toBe('/m/camille');
    const shown = element.querySelector('[data-member-card="camille"]')!;
    expect(shown.textContent).toContain('Camille Durand');
    expect(shown.textContent).toContain('Lyon');
    expect(shown.textContent).toContain('Remote');
    expect(shown.textContent).toContain('Available from');
    expect(shown.textContent).toContain('Speaks Français');
    expect(shown.querySelector('.initials')?.textContent).toBe('CD');

    component.form.controls.availability.setValue({ mode: 'ON_MISSION', date: '2027-03-31' });
    expect(component.dated).toBeTrue();
    component.save();
    expect(network.join).toHaveBeenCalledWith('camille', jasmine.any(Object), { mode: 'ON_MISSION', date: '2027-03-31' });
    expect(notifications.success).toHaveBeenCalledWith('profile.network.saved');
    component.form.controls.availability.setValue({ mode: 'LISTENING', date: '2027-03-31' });
    component.save();
    expect(network.join).toHaveBeenCalledWith('camille', jasmine.any(Object), { mode: 'LISTENING', date: null });
  });

  it('refuses a malformed handle, and says when another member holds it, while typing or on saving', fakeAsync(() => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.form.controls.handle.setValue('Not a handle');
    component.save();
    expect(network.join).not.toHaveBeenCalled();

    network.handle.and.returnValue(of({ handle: 'taken_one', available: false }));
    component.form.controls.handle.setValue('taken_one');
    tick(300);
    expect(component.form.controls.handle.hasError('taken')).toBeTrue();
    network.handle.and.returnValue(of({ handle: 'free_one', available: true }));
    component.form.controls.handle.setValue('free_one');
    tick(300);
    expect(component.form.controls.handle.valid).toBeTrue();

    network.join.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.save();
    expect(component.form.controls.handle.hasError('taken')).toBeTrue();
    component.form.controls.handle.setValue('other_one');
    tick(300);
    network.join.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: { message: "The moderators ended this account's membership" } })));
    component.save();
    expect(notifications.error).toHaveBeenCalledWith('profile.network.banned');
    network.join.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.save();
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');
  }));

  it('verifies the member with the registry, saying why it does not', () => {
    const fixture = create(of(member));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-network-verify]')).not.toBeNull();
    const refusal = (status: number, message?: string) => throwError(() => new HttpErrorResponse({ status, error: { message } }));
    const cases: [number, string | undefined, string][] = [
      [409, 'Add your SIRET in Profile → Billing to be verified', 'profile.network.verifyErrors.noSiret'],
      [409, 'The registry has no active establishment with this SIRET', 'profile.network.verifyErrors.notActive'],
      [409, 'The registry names someone else for this SIRET', 'profile.network.verifyErrors.someoneElse'],
      [503, undefined, 'profile.network.verifyErrors.unavailable'],
      [409, 'Something else', 'profile.network.saveError']
    ];
    for (const [status, message, key] of cases) {
      network.verify.and.returnValue(refusal(status, message));
      component.verify();
      expect(notifications.error).toHaveBeenCalledWith(key);
    }
    network.verify.and.returnValue(of({ ...member, card: { ...card, verified: true } }));
    component.verify();
    fixture.detectChanges();
    expect(notifications.success).toHaveBeenCalledWith('profile.network.verifiedDone');
    expect(element.querySelector('[data-network-verify]')).toBeNull();
    expect(element.querySelector('[data-card-verified]')).not.toBeNull();
  });

  it('lists the members muted and blocked, and lifts either', () => {
    const fixture = create(of(member));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    fixture.detectChanges();
    expect(element.querySelector('[data-network-restrictions]')?.textContent).toContain('You mute and block no member.');
    const leo = { ...card, handle: 'leo_m', firstName: 'Léo' };
    const ines = { ...card, handle: 'ines_b', firstName: 'Inès' };
    component.restrictions = { muted: [leo], blocked: [ines] };
    fixture.detectChanges();
    expect(element.querySelector('[data-lift="muted:leo_m"]')).not.toBeNull();
    network.mute.and.returnValue(of({} as MemberPage));
    (element.querySelector('[data-lift="muted:leo_m"]') as HTMLButtonElement).click();
    expect(network.mute).toHaveBeenCalledWith('leo_m', false);
    expect(component.restrictions.muted).toEqual([]);
    network.block.and.returnValue(of({} as MemberPage));
    component.lift('blocked', ines);
    expect(network.block).toHaveBeenCalledWith('ines_b', false);
    expect(notifications.success).toHaveBeenCalledWith('network.page.unblockedDone');
    network.block.and.returnValue(throwError(() => new Error('down')));
    component.restrictions = { muted: [], blocked: [ines] };
    component.lift('blocked', ines);
    expect(component.restrictions.blocked.length).toBe(1);
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');
  });

  it('sends each kind of notification nowhere, to the app, or by email too', () => {
    const fixture = create(of(member));
    const component = fixture.componentInstance;
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-notice-channel="REPOST"] .mat-button-toggle-checked')?.textContent).toContain('Off');
    expect(component.channel('LIKE')).toBe('EMAIL');
    expect(component.channel('FOLLOW')).toBe('APP');
    network.saveNotificationSettings.and.returnValue(of({ off: ['REPOST', 'LIKE'], emailed: [] }));
    component.setChannel('LIKE', 'OFF');
    expect(network.saveNotificationSettings).toHaveBeenCalledWith(['REPOST', 'LIKE'], []);
    expect(component.noticesOff).toEqual(['REPOST', 'LIKE']);
    expect(component.noticesEmailed).toEqual([]);
    network.saveNotificationSettings.and.returnValue(of({ off: ['LIKE'], emailed: ['REPOST'] }));
    component.setChannel('REPOST', 'EMAIL');
    expect(network.saveNotificationSettings).toHaveBeenCalledWith(['LIKE'], ['REPOST']);
    network.saveNotificationSettings.and.returnValue(of({ off: [] } as unknown as NoticeSettings));
    component.setChannel('LIKE', 'APP');
    expect(network.saveNotificationSettings).toHaveBeenCalledWith([], ['REPOST']);
    expect(component.noticesEmailed).toEqual([]);
    network.saveNotificationSettings.and.returnValue(throwError(() => new Error('down')));
    component.setChannel('FOLLOW', 'OFF');
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');
  });

  it('shows or hides read receipts and who is typing, saying when it cannot', () => {
    const fixture = create(of(member));
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-messaging="readReceipts"] button')?.getAttribute('aria-checked')).toBe('true');
    messagesApi.saveMessaging.and.returnValue(of({ readReceipts: false, typing: true }));
    (element.querySelector('[data-messaging="readReceipts"] button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(messagesApi.saveMessaging).toHaveBeenCalledWith({ readReceipts: false });
    expect(fixture.componentInstance.messaging).toEqual({ readReceipts: false, typing: true });
    messagesApi.saveMessaging.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.setMessaging('typing', false);
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');
    expect(fixture.componentInstance.messaging).toEqual({ readReceipts: false, typing: true });
  });

  it('leaves once confirmed, keeping the handle, and says when the membership cannot be read', () => {
    let fixture = create(of(member));
    const component = fixture.componentInstance;
    confirm.confirm.and.returnValue(of(false));
    component.leave();
    expect(network.leave).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    network.leave.and.returnValue(of(undefined));
    component.leave();
    expect(component.membership).toEqual({ ...member, joined: false, card: null });
    expect(notifications.success).toHaveBeenCalledWith('profile.network.left');
    network.leave.and.returnValue(throwError(() => new Error('down')));
    component.leave();
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');

    TestBed.resetTestingModule();
    fixture = create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('profile.network.loadError');
  });
});
