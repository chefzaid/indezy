import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkDraft, isHeld } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NetworkNavComponent } from '../nav/network-nav.component';

/** The reader's drafts (`/network/drafts`): the scheduled ones first, each to publish now or delete. */
@Component({
  selector: 'app-network-drafts',
  imports: [NetworkNavComponent, DatePipe, MatButtonModule, TranslateModule, PageHeaderComponent],
  templateUrl: './network-drafts.component.html',
  styleUrls: ['./network-drafts.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkDraftsComponent implements OnInit {
  /** Within the reader's own page, as one of its tabs: without the network's tabs and its own header. */
  @Input() embedded = false;

  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);

  drafts: NetworkDraft[] = [];
  loaded = false;

  ngOnInit(): void {
    this.network.drafts().subscribe({
      next: drafts => {
        this.drafts = drafts;
        this.loaded = true;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.drafts.loadError')
    });
  }

  publish(draft: NetworkDraft): void {
    this.network.publishDraft(draft.id).subscribe({
      next: answer => {
        this.drafts = this.drafts.filter(item => item.id !== draft.id);
        this.notifications.success(isHeld(answer) ? 'network.feed.held' : 'network.drafts.published');
      },
      error: () => this.notifications.error('network.feed.postError')
    });
  }

  remove(draft: NetworkDraft): void {
    this.confirm.confirm({ messageKey: 'network.drafts.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (confirmed) {
        this.network.deleteDraft(draft.id).subscribe({
          next: () => this.drafts = this.drafts.filter(item => item.id !== draft.id),
          error: () => this.notifications.error('network.drafts.saveError')
        });
      }
    });
  }
}
