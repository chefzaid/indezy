import { Injectable, inject } from '@angular/core';
import { DOCUMENT, Location } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { Router } from '@angular/router';
import { NotificationService } from '../notification/notification.service';

/** Copies the address of an offer's own page (`/offers/{id}`), to share it. */
@Injectable({ providedIn: 'root' })
export class OfferShareService {
  private readonly clipboard = inject(Clipboard);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly document = inject(DOCUMENT);
  private readonly notifications = inject(NotificationService);

  /** The full address of the offer's page. */
  pageUrl(offerId: number): string {
    const path = this.location.prepareExternalUrl(this.router.serializeUrl(this.router.createUrlTree(['/offers', offerId])));
    return new URL(path, this.document.baseURI).href;
  }

  copyLink(offerId: number): void {
    if (this.clipboard.copy(this.pageUrl(offerId))) {
      this.notifications.success('offers.share.copied');
    } else {
      this.notifications.error('offers.share.failed');
    }
  }
}
