import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SwPush } from '@angular/service-worker';
import { Observable, firstValueFrom, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Web push in this browser: whether it can, whether it is on, and turning it on or off with the server. */
/** What web push tells: the network's notifications, new messages, interviews within the hour, the follow-ups due, new offers. */
export type PushEvent = 'NETWORK' | 'MESSAGES' | 'INTERVIEWS' | 'FOLLOW_UPS' | 'OFFERS';
export const PUSH_EVENTS: readonly PushEvent[] = ['NETWORK', 'MESSAGES', 'INTERVIEWS', 'FOLLOW_UPS', 'OFFERS'];

@Injectable({
  providedIn: 'root'
})
export class WebPushService {
  private readonly http = inject(HttpClient);
  /** Absent without the service worker (development, tests). */
  private readonly swPush = inject(SwPush, { optional: true });
  private readonly API_URL = `${environment.apiUrl}/push`;

  /** Whether this browser runs the app's service worker and can receive pushes. */
  get supported(): boolean {
    return this.swPush?.isEnabled === true;
  }

  /** Whether this browser is subscribed. */
  get subscribed$(): Observable<boolean> {
    return this.swPush ? this.swPush.subscription.pipe(map(subscription => subscription !== null)) : of(false);
  }

  /** The events pushed to the account, each on or off. */
  events(): Observable<Record<PushEvent, boolean>> {
    return this.http.get<Record<PushEvent, boolean>>(`${this.API_URL}/events`);
  }

  /** Turns events on or off, those not named left as they were. */
  setEvents(changes: Partial<Record<PushEvent, boolean>>): Observable<Record<PushEvent, boolean>> {
    return this.http.put<Record<PushEvent, boolean>>(`${this.API_URL}/events`, changes);
  }

  /** The server's VAPID public key; null while web push is not set up. */
  key(): Observable<string | null> {
    return this.http.get<{ publicKey: string | null }>(`${this.API_URL}/key`).pipe(map(answer => answer.publicKey));
  }

  /** Asks the browser's permission, then keeps the subscription on the server. */
  async enable(publicKey: string): Promise<void> {
    if (!this.swPush) {
      throw new Error('No service worker');
    }
    const subscription = await this.swPush.requestSubscription({ serverPublicKey: publicKey });
    await firstValueFrom(this.http.post<void>(`${this.API_URL}/subscriptions`, subscription.toJSON()));
  }

  /** Forgets the subscription on the server, then in the browser. */
  async disable(): Promise<void> {
    if (!this.swPush) {
      return;
    }
    const subscription = await firstValueFrom(this.swPush.subscription);
    if (subscription) {
      await firstValueFrom(this.http.post<void>(`${this.API_URL}/subscriptions/remove`, { endpoint: subscription.endpoint }));
    }
    await this.swPush.unsubscribe();
  }
}
