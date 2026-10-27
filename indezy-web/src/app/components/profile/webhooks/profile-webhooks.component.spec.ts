import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { HttpErrorResponse } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { Webhook, WebhookDelivery } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { WebhookService } from '../../../services/webhook/webhook.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ProfileWebhooksComponent } from './profile-webhooks.component';
import en from '../../../../assets/i18n/en.json';

describe('ProfileWebhooksComponent', () => {
  let service: jasmine.SpyObj<WebhookService>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const webhook = (id: number, changes: Partial<Webhook> = {}): Webhook => ({
    id, url: `https://hooks.example.com/${id}`, events: ['INVOICE_PAID'], active: true, consecutiveFailures: 0,
    lastDeliveryAt: null, lastStatus: null, createdAt: '2026-10-27T10:00:00', ...changes
  });
  const failure = (status: number) => throwError(() => new HttpErrorResponse({ status }));

  function create(list = of([webhook(1, { consecutiveFailures: 2, lastDeliveryAt: '2026-10-27T11:00:00Z', lastStatus: 500 }),
    webhook(2, { active: false })])): ComponentFixture<ProfileWebhooksComponent> {
    service = jasmine.createSpyObj('WebhookService', ['list', 'create', 'update', 'delete', 'ping', 'deliveries']);
    service.list.and.returnValue(list);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ProfileWebhooksComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), { provide: WebhookService, useValue: service }, { provide: Clipboard, useValue: clipboard },
        { provide: ConfirmDialogService, useValue: confirm }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfileWebhooksComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the webhooks with their state and failures', () => {
    const element: HTMLElement = create().nativeElement;
    expect(element.querySelector('[data-webhook="1"]')!.textContent).toContain('2 failure(s) in a row');
    expect(element.querySelector('[data-webhook="1"]')!.textContent).toContain('Invoice paid');
    expect(element.querySelector('[data-webhook="2"]')!.textContent).toContain('Off');
    expect((element.querySelector('[data-webhook="2"] [data-ping]') as HTMLButtonElement).disabled).toBeTrue();
  });

  it('adds a webhook for the chosen events and shows its secret once', () => {
    const fixture = create(of([]));
    const component = fixture.componentInstance;
    component.create();
    expect(service.create).not.toHaveBeenCalled();

    component.form.controls.url.setValue('http://insecure.example.com');
    expect(component.form.invalid).toBeTrue();
    component.form.controls.url.setValue(' https://hooks.example.com/indezy ');
    component.toggle('OPPORTUNITY_STATUS_CHANGED', false);
    component.toggle('INVOICE_PAID', false);
    expect(component.noEvent).toBeTrue();
    component.create();
    expect(service.create).not.toHaveBeenCalled();
    component.toggle('INVOICE_ISSUED', true);
    service.create.and.returnValue(of({ webhook: webhook(3, { events: ['INVOICE_ISSUED'] }), secret: 'a'.repeat(64) }));
    component.create();
    expect(service.create).toHaveBeenCalledWith({ url: 'https://hooks.example.com/indezy', events: ['INVOICE_ISSUED'] });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-webhook-secret]')!.textContent).toBe('a'.repeat(64));
    clipboard.copy.and.returnValue(true);
    component.copy();
    expect(clipboard.copy).toHaveBeenCalledWith('a'.repeat(64));
    expect(notifications.success).toHaveBeenCalledWith('profile.webhooks.copied');
    component.done();
    expect(component.created).toBeUndefined();

    component.form.controls.url.setValue('https://hooks.example.com/again');
    service.create.and.returnValue(failure(400));
    component.create();
    expect(notifications.error).toHaveBeenCalledWith('profile.webhooks.refused');
    service.create.and.returnValue(failure(409));
    component.create();
    expect(notifications.error).toHaveBeenCalledWith('profile.webhooks.saveError');
  });

  it('tests, turns off and on, shows the deliveries and deletes a webhook', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    service.ping.and.returnValue(of({ id: 1 } as WebhookDelivery));
    component.ping(component.list[0]);
    expect(notifications.success).toHaveBeenCalledWith('profile.webhooks.pinged');
    service.ping.and.returnValue(failure(500));
    component.ping(component.list[0]);
    expect(notifications.error).toHaveBeenCalledWith('profile.webhooks.saveError');

    service.update.and.returnValue(of(webhook(1, { active: false })));
    component.setActive(component.list[0], false);
    expect(service.update).toHaveBeenCalledWith(1, { url: 'https://hooks.example.com/1', events: ['INVOICE_PAID'], active: false });
    expect(component.list[0].active).toBeFalse();
    service.update.and.returnValue(failure(500));
    component.setActive(component.list[0], true);
    expect(notifications.error).toHaveBeenCalledWith('profile.webhooks.saveError');

    service.deliveries.and.returnValue(of([{ id: 9, event: 'PING', status: 'FAILED', attempts: 6, nextAttemptAt: '2026-10-27T10:00:00Z',
      lastAttemptAt: '2026-10-27T10:00:00Z', responseStatus: 500, error: 'Answered 500', createdAt: '2026-10-27T09:00:00' }]));
    component.showDeliveries(component.list[0]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-deliveries]')!.textContent).toContain('Failed (500)');
    component.showDeliveries(component.list[0]);
    expect(component.deliveries.has(1)).toBeFalse();
    service.deliveries.and.returnValue(failure(500));
    component.showDeliveries(component.list[0]);
    expect(notifications.error).toHaveBeenCalledWith('profile.webhooks.loadError');

    service.delete.and.returnValue(of(undefined));
    confirm.confirm.and.returnValue(of(false));
    component.remove(component.list[0]);
    expect(service.delete).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    component.remove(component.list[0]);
    expect(component.list.map(item => item.id)).toEqual([2]);
    service.delete.and.returnValue(failure(500));
    component.remove(component.list[0]);
    expect(notifications.error).toHaveBeenCalledWith('profile.webhooks.saveError');
  });

  it('says when the webhooks cannot be loaded, and when there are five', () => {
    create(failure(500));
    expect(notifications.error).toHaveBeenCalledWith('profile.webhooks.loadError');
    TestBed.resetTestingModule();
    const full = create(of([1, 2, 3, 4, 5].map(id => webhook(id))));
    expect((full.nativeElement as HTMLElement).querySelector('[data-webhooks-full]')).not.toBeNull();
  });
});
