import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BehaviorSubject, Observable, catchError, of } from 'rxjs';
import { OfferService } from './offer.service';

/** How often the badge on Offers counts again. */
export const OFFER_ALERTS_REFRESH_MS = 5 * 60 * 1000;

/** The offers new for the workspace's saved searches, behind the badge on Offers in the navigation. */
@Injectable({
  providedIn: 'root'
})
export class OfferAlertsService {
  private readonly offerService = inject(OfferService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly count$ = new BehaviorSubject(0);
  private timer: ReturnType<typeof setInterval> | null = null;
  readonly newOffers$: Observable<number> = this.count$.asObservable();

  constructor() {
    this.destroyRef.onDestroy(() => this.stop());
  }

  /** Counts now, then every few minutes, until {@link stop}. */
  start(): void {
    this.refresh();
    if (this.timer === null) {
      this.timer = setInterval(() => this.refresh(), OFFER_ALERTS_REFRESH_MS);
    }
  }

  stop(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.count$.next(0);
  }

  /** Counts again, after a saved search was saved, opened or deleted; a failure leaves the badge as it was. */
  refresh(): void {
    this.offerService.alerts()
      .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe(alerts => {
        if (alerts) {
          this.count$.next(alerts.newOffers);
        }
      });
  }
}
