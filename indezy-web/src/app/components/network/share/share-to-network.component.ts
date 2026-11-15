import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { PostDetails, PostKind } from '../../../models';
import { CapabilitiesService } from '../../../services/admin/capabilities.service';

/** What a page hands the feed's composer to share: a text, of a kind with its facts. */
export interface NetworkShare {
  text: string;
  kind: PostKind;
  details: PostDetails | null;
}

/** The router state the feed reads its shared draft from. */
export const SHARE_STATE = 'networkShare';

/** **Share to the network**: opens the feed with the composer filled in, to read again before publishing. Shown while the network is on. */
@Component({
  selector: 'app-share-to-network',
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  template: `@if (networkOn) {
    <button mat-stroked-button type="button" (click)="share()" data-share-to-network>
      <mat-icon>diversity_3</mat-icon>{{ 'network.share.action' | translate }}
    </button>
  }`,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ShareToNetworkComponent {
  private readonly router = inject(Router);
  private readonly capabilities = inject(CapabilitiesService);

  /** What to share, built when the button is pressed. */
  @Input({ required: true }) build!: () => NetworkShare;

  get networkOn(): boolean {
    return this.capabilities.snapshot().features.SOCIAL_NETWORK === true;
  }

  share(): void {
    void this.router.navigate(['/network'], { state: { [SHARE_STATE]: this.build() } });
  }
}
