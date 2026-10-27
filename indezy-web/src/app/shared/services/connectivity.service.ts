import { Injectable, signal } from '@angular/core';

/** Whether the browser has a connection, as it reports it, updated when it comes and goes. */
@Injectable({
  providedIn: 'root'
})
export class ConnectivityService {
  private readonly onlineState = signal(globalThis.navigator?.onLine ?? true);
  readonly online = this.onlineState.asReadonly();

  constructor() {
    globalThis.addEventListener?.('online', () => this.onlineState.set(true));
    globalThis.addEventListener?.('offline', () => this.onlineState.set(false));
  }
}
