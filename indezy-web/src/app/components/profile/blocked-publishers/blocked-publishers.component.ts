import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { OfferPublisherBlock, publisherKey } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';

/** The profile's blocklist: publishers whose offers stay out of the offer feed, added by name or from an offer's menu. */
@Component({
  selector: 'app-blocked-publishers',
  imports: [FormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule],
  templateUrl: './blocked-publishers.component.html',
  styleUrls: ['./blocked-publishers.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BlockedPublishersComponent implements OnInit {
  blocks: OfferPublisherBlock[] = [];
  loading = true;
  failed = false;
  saving = false;
  name = '';

  private readonly offerService = inject(OfferService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.offerService.blockedPublishers().subscribe({
      next: blocks => {
        this.blocks = blocks;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: () => {
        this.failed = true;
        this.loading = false;
        this.changes.markForCheck();
      }
    });
  }

  /** The typed name has a letter or a digit. */
  get canAdd(): boolean {
    return !this.saving && publisherKey(this.name) !== '';
  }

  add(): void {
    if (!this.canAdd) {
      return;
    }
    this.saving = true;
    this.offerService.blockPublisher(this.name.trim()).subscribe({
      next: block => {
        this.saving = false;
        this.name = '';
        if (!this.blocks.some(known => known.id === block.id)) {
          this.blocks = [...this.blocks, block].sort((a, b) => a.name.localeCompare(b.name));
        }
        this.notifications.success('offers.notifications.publisherBlocked', 4000, { publisher: block.name });
        this.changes.markForCheck();
      },
      error: () => {
        this.saving = false;
        this.changes.markForCheck();
      }
    });
  }

  remove(block: OfferPublisherBlock): void {
    const before = this.blocks;
    this.blocks = this.blocks.filter(known => known.id !== block.id);
    this.changes.markForCheck();
    this.offerService.unblockPublisher(block.id).subscribe({
      next: () => this.notifications.success('profile.blockedPublishers.unblocked', 4000, { publisher: block.name }),
      error: () => {
        this.blocks = before;
        this.changes.markForCheck();
      }
    });
  }
}
