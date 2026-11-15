import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BehaviorSubject, Observable, catchError, of } from 'rxjs';
import { NetworkService } from './network.service';

/** How often the badge on Network counts the unread notifications again. */
export const NETWORK_NOTICES_REFRESH_MS = 60 * 1000;

/** The member's unread network notifications, behind the badge on Network in the navigation; none for a non-member. */
@Injectable({
  providedIn: 'root'
})
export class NetworkNoticesService {
  private readonly network = inject(NetworkService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly count$ = new BehaviorSubject(0);
  private timer: ReturnType<typeof setInterval> | null = null;
  readonly unread$: Observable<number> = this.count$.asObservable();

  constructor() {
    this.destroyRef.onDestroy(() => this.stop());
  }

  /** Counts now, then every minute, until {@link stop}. */
  start(): void {
    this.refresh();
    if (this.timer === null) {
      this.timer = setInterval(() => this.refresh(), NETWORK_NOTICES_REFRESH_MS);
    }
  }

  stop(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.count$.next(0);
  }

  /** Counts again; a non-member, or a failure, counts none. */
  refresh(): void {
    this.network.unreadNotifications().pipe(catchError(() => of({ unread: 0 })), takeUntilDestroyed(this.destroyRef))
      .subscribe(answer => this.count$.next(answer?.unread ?? 0));
  }
}
