import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { map, switchMap } from 'rxjs';

import { NetworkPost, TagPage } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { PostCardComponent } from '../post-card/post-card.component';

/** A tag's page (`/network/tags/<tag>`): following it, the posts naming it and the members whose cards show the skill. */
@Component({
  selector: 'app-tag-page',
  imports: [PageHeaderComponent, RouterLink, MatButtonModule, MatIconModule, TranslateModule, PostCardComponent],
  templateUrl: './tag-page.component.html',
  styleUrls: ['./tag-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class TagPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  page: TagPage | null = null;
  posts: NetworkPost[] = [];

  ngOnInit(): void {
    this.route.paramMap.pipe(map(params => params.get('tag') ?? ''), switchMap(tag => this.network.tagPage(tag)),
      takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => this.show(page),
      error: () => this.notifications.error('network.tags.loadError')
    });
  }

  more(): void {
    this.network.tagPage(this.page!.tag, this.page!.posts.page + 1).subscribe({
      next: page => {
        this.posts = [...this.posts, ...page.posts.posts];
        this.page = page;
      },
      error: () => this.notifications.error('network.tags.loadError')
    });
  }

  toggleFollow(): void {
    const page = this.page!;
    this.network.followTag(page.tag, !page.followed).subscribe({
      next: tags => this.page = { ...page, followed: tags.includes(page.tag) },
      error: () => this.notifications.error('network.tags.followError')
    });
  }

  private show(page: TagPage): void {
    this.page = page;
    this.posts = page.posts.posts;
    this.changes.markForCheck();
  }
}
