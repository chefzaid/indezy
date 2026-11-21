import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { WebPushService } from './web-push.service';

describe('WebPushService', () => {
  const url = `${environment.apiUrl}/push`;

  it('subscribes with the server\'s key and keeps the subscription, then forgets it', async () => {
    const subscription = { endpoint: 'https://fcm.googleapis.com/fcm/send/x', toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/fcm/send/x',
      keys: { p256dh: 'p', auth: 'a' } }) } as unknown as PushSubscription;
    const swPush = jasmine.createSpyObj('SwPush', ['requestSubscription', 'unsubscribe'], { isEnabled: true, subscription: of(subscription) });
    swPush.requestSubscription.and.resolveTo(subscription);
    swPush.unsubscribe.and.resolveTo();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SwPush, useValue: swPush }] });
    const service = TestBed.inject(WebPushService);
    const http = TestBed.inject(HttpTestingController);
    expect(service.supported).toBeTrue();
    expect(await firstValueFrom(service.subscribed$)).toBeTrue();

    service.key().subscribe(key => expect(key).toBe('BKey'));
    http.expectOne(`${url}/key`).flush({ publicKey: 'BKey' });

    const enabling = service.enable('BKey');
    await Promise.resolve();
    await Promise.resolve();
    const kept = http.expectOne(`${url}/subscriptions`);
    expect(kept.request.body).toEqual({ endpoint: 'https://fcm.googleapis.com/fcm/send/x', keys: { p256dh: 'p', auth: 'a' } });
    kept.flush(null);
    await enabling;
    expect(swPush.requestSubscription).toHaveBeenCalledWith({ serverPublicKey: 'BKey' });

    const disabling = service.disable();
    await Promise.resolve();
    http.expectOne(`${url}/subscriptions/remove`).flush(null);
    await disabling;
    expect(swPush.unsubscribe).toHaveBeenCalled();
  });

  it('does nothing without the service worker', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(WebPushService);
    expect(service.supported).toBeFalse();
    expect(await firstValueFrom(service.subscribed$)).toBeFalse();
    await expectAsync(service.enable('BKey')).toBeRejected();
    await service.disable();
  });
});
