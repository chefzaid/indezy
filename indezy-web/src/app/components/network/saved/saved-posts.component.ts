import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkFile, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { PostCardComponent } from '../post-card/post-card.component';
import { NetworkFilesComponent } from '../files/network-files.component';

/** The reader's saved posts (`/network/saved`), only theirs, filtered by tag, or the documents they carry. */
@Component({
  selector: 'app-saved-posts',
  imports: [PageHeaderComponent, MatButtonModule, MatChipsModule, TranslateModule, PostCardComponent, NetworkFilesComponent],
  templateUrl: './saved-posts.component.html',
  styleUrls: ['./saved-posts.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SavedPostsComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  posts: NetworkPost[] = [];
  tags: string[] = [];
  tag: string | null = null;
  page = 0;
  more = false;
  loaded = false;
  /** The documents of the saved posts shown, rather than the posts. */
  documentsOnly = false;

  /** The documents the saved posts loaded carry, in their order. */
  get documents(): NetworkFile[] {
    return this.posts.flatMap(post => (post.files ?? []).filter(file => file.kind === 'DOCUMENT'));
  }

  showDocuments(on: boolean): void {
    this.documentsOnly = on;
  }

  ngOnInit(): void {
    this.load(0);
  }

  filter(tag: string | null): void {
    this.tag = tag;
    this.load(0);
  }

  load(page: number): void {
    this.network.saved(this.tag, page).subscribe({
      next: saved => {
        this.posts = page === 0 ? saved.page.posts : [...this.posts, ...saved.page.posts];
        this.tags = saved.tags;
        this.page = saved.page.page;
        this.more = saved.page.more;
        this.loaded = true;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.savedPosts.loadError')
    });
  }

  /** A post taken off the list leaves the page. */
  changed(post: NetworkPost): void {
    if (!post.bookmarked) {
      this.posts = this.posts.filter(item => item.id !== post.id);
    }
  }
}
