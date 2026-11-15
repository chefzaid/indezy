import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';

import { MemberCard, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * Under a mission passed on: **Add to my pipeline**, telling its author who took it or not, then a link to the
 * opportunity; to its author, how many members took it and, on request, those who let them know.
 */
@Component({
  selector: 'app-mission-take',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatMenuModule, TranslateModule],
  templateUrl: './mission-take.component.html',
  styles: [`.take { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 4px 0 8px; }
    .takers { margin: 0; padding-left: 20px; font-size: 0.875rem; }`],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MissionTakeComponent {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);

  @Input({ required: true }) post!: NetworkPost;

  /** The opportunity the mission became in this session. */
  projectId: number | null = null;
  takers: MemberCard[] | null = null;
  taking = false;

  take(named: boolean): void {
    this.taking = true;
    this.network.take(this.post.id, named).subscribe({
      next: project => {
        this.taking = false;
        this.projectId = project.id ?? null;
        this.post = { ...this.post, taken: { count: this.post.taken?.count ?? null, mine: true } };
        this.notifications.success('network.pipeline.added');
      },
      error: (error: HttpErrorResponse) => {
        this.taking = false;
        this.notifications.error(error.status === 409 ? 'network.pipeline.already' : 'network.pipeline.error');
      }
    });
  }

  /** The members who took the reader's own mission and let them know, or closes their list. */
  toggleTakers(): void {
    if (this.takers) {
      this.takers = null;
      return;
    }
    this.network.takers(this.post.id).subscribe({
      next: takers => this.takers = takers,
      error: () => this.notifications.error('network.pipeline.error')
    });
  }
}
