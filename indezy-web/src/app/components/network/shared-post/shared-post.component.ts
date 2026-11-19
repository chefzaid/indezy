import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';

/**
 * A network post sent in a message, as a small card in the conversation: its author and its first words, opening the post;
 * "This post is no longer available" once deleted or out of the reader's reach.
 */
@Component({
  selector: 'app-shared-post',
  imports: [RouterLink, MatIconModule, TranslateModule, MemberAvatarComponent],
  template: `
    @if (post; as shown) {
      <a class="shared" [routerLink]="['/network/posts', shown.id]" data-shared-post>
        <span class="who"><app-member-avatar [subject]="shown.author" [size]="24"></app-member-avatar>
          {{ shown.author.firstName }} {{ shown.author.lastName }}</span>
        <span class="text">{{ shown.text.slice(0, 220) }}{{ shown.text.length > 220 ? '…' : '' }}</span>
      </a>
    } @else if (gone) {
      <span class="shared gone" data-shared-post-gone><mat-icon aria-hidden="true">block</mat-icon>{{ 'network.send.gone' | translate }}</span>
    }
  `,
  styles: [`
    .shared { display: flex; flex-direction: column; gap: 4px; margin-top: 6px; padding: 10px 12px; border: 1px solid var(--app-border);
      border-radius: 12px; background: var(--app-surface); color: var(--app-text); text-decoration: none; }
    a.shared:hover { border-color: var(--app-link); }
    .who { display: inline-flex; align-items: center; gap: 6px; font-size: 0.8125rem; font-weight: 600; }
    .text { color: var(--app-text-muted); font-size: 0.8125rem; line-height: 1.4; white-space: pre-line; }
    .gone { flex-direction: row; align-items: center; color: var(--app-text-muted); font-size: 0.8125rem; }
    .gone mat-icon { width: 18px; height: 18px; font-size: 18px; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SharedPostComponent implements OnChanges {
  private readonly network = inject(NetworkService);
  private readonly changes = inject(ChangeDetectorRef);

  @Input({ required: true }) postId!: number;
  post: NetworkPost | null = null;
  gone = false;

  ngOnChanges(): void {
    this.post = null;
    this.gone = false;
    this.network.onePost(this.postId, true).subscribe({
      next: post => {
        this.post = post;
        this.changes.markForCheck();
      },
      error: () => {
        this.gone = true;
        this.changes.markForCheck();
      }
    });
  }
}
