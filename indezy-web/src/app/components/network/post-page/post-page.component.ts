import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { map, switchMap } from 'rxjs';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { PostCardComponent } from '../post-card/post-card.component';

/** A post's own page (`/network/posts/:id`): the post with its comments open, reached from notifications, search and links. */
@Component({
  selector: 'app-post-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, TranslateModule, PostCardComponent],
  template: `<div class="post-page">
    <a mat-button routerLink="/network" class="back"><mat-icon>arrow_back</mat-icon>{{ 'network.postPage.back' | translate }}</a>
    @if (post) {
      <app-post-card [post]="post" [commentsOpen]="true" (deleted)="remove($event)" data-post-page></app-post-card>
    } @else if (missing) {
      <section class="missing" data-post-missing>
        <mat-icon aria-hidden="true">search_off</mat-icon>
        <p>{{ 'network.postPage.missing' | translate }}</p>
      </section>
    }
  </div>`,
  styles: [`.post-page { display: flex; flex-direction: column; gap: 12px; max-width: 820px; margin: 0 auto; }
    .back { align-self: flex-start; }
    .missing { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 32px 16px; color: var(--app-text-muted);
      text-align: center; border: 1px solid var(--app-border); border-radius: 16px; background: var(--app-surface); }
    .missing mat-icon { width: 40px; height: 40px; font-size: 40px; }`],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PostPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  post: NetworkPost | null = null;
  /** The post is gone, or the reader cannot read it. */
  missing = false;

  ngOnInit(): void {
    this.route.paramMap.pipe(map(params => Number(params.get('id'))), switchMap(id => this.network.onePost(id)),
      takeUntilDestroyed(this.destroyRef)).subscribe({
      next: post => {
        this.post = post;
        this.missing = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.post = null;
        this.missing = error.status === 404;
        if (!this.missing) {
          this.notifications.error('network.feed.loadError');
        }
        this.changes.markForCheck();
      }
    });
  }

  remove(post: NetworkPost): void {
    this.confirm.confirm({ messageKey: 'network.feed.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (confirmed) {
        this.network.deletePost(post.id).subscribe({
          next: () => {
            this.notifications.success('network.feed.deleted');
            void this.router.navigate(['/network']);
          },
          error: () => this.notifications.error('network.feed.postError')
        });
      }
    });
  }
}
