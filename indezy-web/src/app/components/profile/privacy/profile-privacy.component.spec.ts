import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MessagingSettings, NetworkMembership, PageViews } from '../../../models';
import { UserNotificationSettings } from '../../../models/user-management.models';
import { CapabilitiesService, DEFAULT_CAPABILITIES } from '../../../services/admin/capabilities.service';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import en from '../../../../assets/i18n/en.json';
import { ProfilePrivacyComponent, ProfileTab } from './profile-privacy.component';

describe('ProfilePrivacyComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let messages: jasmine.SpyObj<NetworkMessagesService>;
  let users: jasmine.SpyObj<UserManagementService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const membership = (changes: Partial<NetworkMembership> = {}): NetworkMembership => ({ joined: true, handle: 'camille', fields: {} as never,
    availability: {} as never, card: null, privateCard: false, esnSuggestions: false, ...changes });
  const messaging: MessagingSettings = { readReceipts: true, typing: true, presence: false, requests: true };
  const emails: UserNotificationSettings = { weeklyReports: true, weeklyDigestSections: [], bookingEmails: false, newOffersBadge: true,
    messageEmails: true, emailsAvailable: true };

  function create(joined = true, networkOn = true) {
    network = jasmine.createSpyObj('NetworkService', ['membership', 'pageViews', 'setContactSuggestions', 'setEsnSuggestions', 'setPrivate',
      'setPrivateViewing']);
    network.membership.and.returnValue(of(membership({ joined })));
    network.pageViews.and.returnValue(of({ privateViewing: false } as PageViews));
    messages = jasmine.createSpyObj('NetworkMessagesService', ['messaging', 'saveMessaging']);
    messages.messaging.and.returnValue(of(messaging));
    users = jasmine.createSpyObj('UserManagementService', ['getNotificationSettings', 'updateNotificationSettings']);
    users.getNotificationSettings.and.returnValue(of(emails));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({ imports: [ProfilePrivacyComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: NetworkService, useValue: network },
        { provide: NetworkMessagesService, useValue: messages }, { provide: UserManagementService, useValue: users },
        { provide: NotificationService, useValue: notifications },
        { provide: CapabilitiesService, useValue: { snapshot: () => ({ admin: false, features: { ...DEFAULT_CAPABILITIES.features, SOCIAL_NETWORK: networkOn } }) } }] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfilePrivacyComponent);
    fixture.detectChanges();
    return fixture;
  }

  const toggle = (element: HTMLElement, key: string) => element.querySelector(`[data-privacy-switch="${key}"] button[role="switch"]`) as HTMLButtonElement;

  it('gathers every switch, as each is saved, contact suggestions on by default', () => {
    const element: HTMLElement = create().nativeElement;
    for (const key of ['contactSuggestions', 'esnSuggestions', 'privateCard', 'privateViewing', 'requests', 'readReceipts', 'typing', 'presence',
      'weeklyReports', 'messageEmails', 'bookingEmails']) {
      expect(element.querySelector(`[data-privacy-switch="${key}"]`)).withContext(key).not.toBeNull();
    }
    expect(toggle(element, 'contactSuggestions').getAttribute('aria-checked')).toBe('true');
    expect(toggle(element, 'esnSuggestions').getAttribute('aria-checked')).toBe('false');
    expect(toggle(element, 'presence').getAttribute('aria-checked')).toBe('false');
    expect(toggle(element, 'bookingEmails').getAttribute('aria-checked')).toBe('false');
    expect(element.querySelector('[data-privacy-switch="contactSuggestions"]')?.textContent).toContain('Suggestions by my contacts');
  });

  it('saves each switch where it belongs, and puts it back when it fails', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    network.setContactSuggestions.and.returnValue(of(membership({ contactSuggestions: false })));
    toggle(element, 'contactSuggestions').click();
    fixture.detectChanges();
    expect(network.setContactSuggestions).toHaveBeenCalledWith(false);
    expect(toggle(element, 'contactSuggestions').getAttribute('aria-checked')).toBe('false');
    expect(notifications.success).toHaveBeenCalledWith('profile.privacy.saved');
    messages.saveMessaging.and.returnValue(of({ ...messaging, presence: true }));
    toggle(element, 'presence').click();
    expect(messages.saveMessaging).toHaveBeenCalledWith({ presence: true });
    network.setPrivateViewing.and.returnValue(of({ privateViewing: true } as PageViews));
    toggle(element, 'privateViewing').click();
    expect(network.setPrivateViewing).toHaveBeenCalledWith(true);
    users.updateNotificationSettings.and.returnValue(throwError(() => new Error('down')));
    toggle(element, 'weeklyReports').click();
    fixture.detectChanges();
    expect(users.updateNotificationSettings).toHaveBeenCalledWith({ weeklyReports: false });
    expect(notifications.error).toHaveBeenCalledWith('profile.privacy.saveError');
    expect(toggle(element, 'weeklyReports').getAttribute('aria-checked')).toBe('true');
  });

  it('leads to the tab of the settings that are not switches', () => {
    const fixture = create();
    const opened: ProfileTab[] = [];
    fixture.componentInstance.open.subscribe(tab => opened.push(tab));
    (fixture.nativeElement.querySelector('[data-privacy-elsewhere="contactRetention"] button') as HTMLButtonElement).click();
    (fixture.nativeElement.querySelector('[data-privacy-elsewhere="cardFields"] button') as HTMLButtonElement).click();
    (fixture.nativeElement.querySelector('[data-privacy-elsewhere="exportDelete"] button') as HTMLButtonElement).click();
    expect(opened).toEqual(['preferences', 'network', 'account']);
  });

  it('says the network\'s settings do not apply outside it, and leaves them out while it is off', () => {
    let element: HTMLElement = create(false).nativeElement;
    expect(element.querySelector('[data-privacy-not-member]')).not.toBeNull();
    expect(element.querySelector('[data-privacy-switch="contactSuggestions"]')).toBeNull();
    TestBed.resetTestingModule();
    element = create(true, false).nativeElement;
    expect(network.membership).not.toHaveBeenCalled();
    expect(element.querySelector('[data-privacy-switch="messageEmails"]')).toBeNull();
    expect(element.querySelector('[data-privacy-switch="weeklyReports"]')).not.toBeNull();
  });
});
