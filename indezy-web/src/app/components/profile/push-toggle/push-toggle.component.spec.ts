import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import en from '../../../../assets/i18n/en.json';
import { NotificationService } from '../../../services/notification/notification.service';
import { WebPushService } from '../../../services/push/web-push.service';
import { PushToggleComponent } from './push-toggle.component';

describe('PushToggleComponent', () => {
  let push: jasmine.SpyObj<WebPushService> & { supported: boolean };
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(supported = true, key: string | null = 'BKey', subscribed = false): ComponentFixture<PushToggleComponent> {
    push = Object.assign(jasmine.createSpyObj('WebPushService', ['key', 'enable', 'disable', 'events', 'setEvents']), { supported,
      subscribed$: of(subscribed) });
    push.events.and.returnValue(of({ NETWORK: true, MESSAGES: true, INTERVIEWS: true, FOLLOW_UPS: true, OFFERS: true }));
    push.key.and.returnValue(key === undefined ? throwError(() => new Error('down')) : of(key));
    push.enable.and.resolveTo();
    push.disable.and.resolveTo();
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [PushToggleComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: WebPushService, useValue: push },
        { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(PushToggleComponent);
    fixture.detectChanges();
    return fixture;
  }

  const change = (checked: boolean) => ({ checked, source: { checked } }) as unknown as MatSlideToggleChange;

  it('turns push on and off in this browser with the server\'s key', async () => {
    const component = create().componentInstance;
    await component.toggle(change(true));
    expect(push.enable).toHaveBeenCalledWith('BKey');
    expect(component.on).toBeTrue();
    expect(notifications.success).toHaveBeenCalledWith('profile.push.enabled');
    await component.toggle(change(false));
    expect(push.disable).toHaveBeenCalled();
    expect(component.on).toBeFalse();
  });

  it('puts the switch back when the browser refuses', async () => {
    const component = create().componentInstance;
    push.enable.and.rejectWith(new Error('denied'));
    const event = change(true);
    await component.toggle(event);
    expect(event.source.checked).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('profile.push.error');
    expect(component.saving).toBeFalse();
  });

  it('says why push is not available: the browser or the server', () => {
    const browser = create(false);
    expect(browser.nativeElement.querySelector('[data-push-unavailable="browser"]')).not.toBeNull();
    expect(push.key).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    const server = create(true, null);
    expect(server.nativeElement.querySelector('[data-push-unavailable="server"]')).not.toBeNull();
    expect(server.componentInstance.publicKey).toBeNull();
  });

  it('turns each pushed event on or off for the account once push is on, put back when it cannot be', () => {
    const fixture = create(true, 'BKey', true);
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('[data-push-event]').length).toBe(5);
    expect(element.querySelector('[data-push-event="FOLLOW_UPS"]')!.textContent).toContain('follow-ups due');
    push.setEvents.and.returnValue(of({ NETWORK: true, MESSAGES: false, INTERVIEWS: true, FOLLOW_UPS: true, OFFERS: true }));
    const checkbox = { checked: false, source: { checked: false } } as unknown as import('@angular/material/checkbox').MatCheckboxChange;
    fixture.componentInstance.setEvent('MESSAGES', checkbox);
    expect(push.setEvents).toHaveBeenCalledWith({ MESSAGES: false });
    expect(fixture.componentInstance.events?.MESSAGES).toBeFalse();
    push.setEvents.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.setEvent('NETWORK', checkbox);
    expect(checkbox.source.checked).toBeTrue();
    expect(notifications.error).toHaveBeenCalledWith('profile.push.error');
  });

  it('hides the events while push is off in this browser', () => {
    const fixture = create();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-push-events]')).toBeNull();
  });
});
