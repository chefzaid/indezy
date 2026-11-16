import { Injectable, NgZone, OnDestroy, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';

/** What changed in the network: which conversation, or that a notification came; never a message's text. */
export interface NetworkEvent {
  type: 'message' | 'read' | 'typing' | 'notice';
  conversationId?: number;
  /** Who is typing, by their first name. */
  name?: string;
}

/** How long to wait before opening the stream again: twice longer after each failure, a minute at most. */
export const STREAM_RETRY_MS = 2_000;
export const STREAM_RETRY_MAX_MS = 60_000;
/** A stream that ends sooner than this counts as a failure, so that one closed at once is not opened again in a loop. */
export const STREAM_SHORT_MS = 5_000;

/**
 * The member's stream of what changes in the network (server-sent events, read with `fetch` so that it carries the
 * token): opened by {@link start}, opened again after it ends or fails; while it is closed, the pages poll as before.
 */
@Injectable({
  providedIn: 'root'
})
export class NetworkStreamService implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);
  private readonly event$ = new Subject<NetworkEvent>();
  private readonly connected$ = new BehaviorSubject(false);
  private controller: AbortController | null = null;
  private retry: ReturnType<typeof setTimeout> | null = null;
  private failures = 0;

  readonly events: Observable<NetworkEvent> = this.event$.asObservable();
  readonly open: Observable<boolean> = this.connected$.asObservable();

  /** Whether the stream is open: the pages then wait for its events rather than polling. */
  get connected(): boolean {
    return this.connected$.value;
  }

  /** Opens the stream, unless it is open or opening. */
  start(): void {
    if (this.controller === null && this.retry === null && typeof fetch === 'function' && typeof TextDecoder === 'function') {
      void this.connect();
    }
  }

  stop(): void {
    if (this.retry !== null) {
      clearTimeout(this.retry);
      this.retry = null;
    }
    this.controller?.abort();
    this.controller = null;
    this.failures = 0;
    this.connected$.next(false);
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private async connect(): Promise<void> {
    const token = this.auth.getToken();
    if (!token) {
      return;
    }
    const controller = new AbortController();
    this.controller = controller;
    // Signed out, away from the network, or the network turned off: not opened again until the next start.
    let refused = false;
    try {
      const response = await fetch(`${environment.apiUrl}/network/stream`, {
        headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` }, cache: 'no-store', signal: controller.signal
      });
      refused = [401, 403, 404].includes(response.status);
      if (!response.ok || !response.body) {
        throw new Error(`stream ${response.status}`);
      }
      const opened = Date.now();
      await this.read(response.body.getReader());
      this.failures = Date.now() - opened < STREAM_SHORT_MS ? this.failures + 1 : 0;
    } catch {
      this.failures++;
    }
    if (this.controller === controller && refused) {
      this.controller = null;
      this.failures = 0;
    } else if (this.controller === controller) {
      this.controller = null;
      this.connected$.next(false);
      const delay = this.failures === 0 ? 0 : Math.min(STREAM_RETRY_MAX_MS, STREAM_RETRY_MS * 2 ** (this.failures - 1));
      this.retry = setTimeout(() => {
        this.retry = null;
        void this.connect();
      }, delay);
    }
  }

  /** Reads events until the stream ends: a name line, a data line, then a blank line; comments are heartbeats. */
  private async read(reader: ReadableStreamDefaultReader<Uint8Array>): Promise<void> {
    const decoder = new TextDecoder();
    let buffer = '';
    let name = '';
    let data = '';
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        return;
      }
      buffer += decoder.decode(value, { stream: true });
      let end: number;
      while ((end = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, end).replace(/\r$/, '');
        buffer = buffer.slice(end + 1);
        if (line === '') {
          this.dispatch(name, data);
          name = '';
          data = '';
        } else if (line.startsWith('event:')) {
          name = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          data += line.slice(5).trim();
        }
      }
    }
  }

  private dispatch(name: string, data: string): void {
    if (name === 'ready') {
      this.zone.run(() => this.connected$.next(true));
      return;
    }
    if (!name || !data) {
      return;
    }
    try {
      const event = JSON.parse(data) as NetworkEvent;
      this.zone.run(() => this.event$.next({ ...event, type: name as NetworkEvent['type'] }));
    } catch {
      // An event that cannot be read is dropped; the next poll or event brings the page up to date.
    }
  }
}
