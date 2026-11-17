import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

/**
 * **Back** at the top of the network's pages: to the page the reader came from within the app, or to the feed (or the
 * page given) when they opened this one first, from a link or a bookmark.
 */
@Component({
  selector: 'app-network-back',
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  template: `<button mat-button type="button" class="network-back" (click)="back()" data-network-back>
    <mat-icon>arrow_back</mat-icon>{{ 'network.back' | translate }}</button>`,
  styles: [`:host { display: block; }
    .network-back { margin-left: -8px; color: var(--app-text-muted); }`],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NetworkBackComponent {
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  /** Where Back goes when there is no page to go back to. */
  @Input() fallback = '/network';

  back(): void {
    if (this.router.lastSuccessfulNavigation()?.previousNavigation) {
      this.location.back();
    } else {
      void this.router.navigateByUrl(this.fallback);
    }
  }
}
