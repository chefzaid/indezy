import { ChangeDetectionStrategy, Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { Booking, BookingPage, BookingSettings } from '../../../models';
import { BookingService } from '../../../services/booking/booking.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { BookingSettingsDialogComponent } from './booking-settings-dialog.component';

/** The booking link under the calendar: its address to share, its settings, and the calls booked, each cancellable. */
@Component({
  selector: 'app-booking-link',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatIconModule, TranslateModule],
  templateUrl: './booking-link.component.html',
  styleUrls: ['./booking-link.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class BookingLinkComponent implements OnInit {
  /** A call was cancelled or the link changed: the calendar reads its calls again. */
  @Output() readonly changed = new EventEmitter<void>();

  private readonly bookingService = inject(BookingService);
  private readonly dialog = inject(MatDialog);
  private readonly clipboard = inject(Clipboard);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);

  page: BookingPage | null = null;
  calls: Booking[] = [];

  get url(): string | null {
    return this.page?.slug ? `${globalThis.location.origin}/book/${this.page.slug}` : null;
  }

  ngOnInit(): void {
    this.bookingService.page().subscribe({
      next: page => this.page = page,
      error: () => this.notifications.error('booking.loadError')
    });
    this.loadCalls();
  }

  edit(): void {
    this.dialog.open<BookingSettingsDialogComponent, BookingSettings, BookingSettings>(BookingSettingsDialogComponent, {
      data: this.page!.settings, width: '620px', maxWidth: '95vw'
    }).afterClosed().subscribe(settings => {
      if (!settings) {
        return;
      }
      this.bookingService.save(settings).subscribe({
        next: page => {
          this.page = page;
          this.notifications.success('booking.saved');
        },
        error: () => this.notifications.error('booking.saveError')
      });
    });
  }

  copy(): void {
    if (this.url && this.clipboard.copy(this.url)) {
      this.notifications.success('booking.copied');
    }
  }

  renew(): void {
    this.confirm.confirm({ titleKey: 'booking.renewTitle', messageKey: 'booking.renewMessage', confirmKey: 'booking.renew', danger: true })
      .subscribe(confirmed => {
        if (!confirmed) {
          return;
        }
        this.bookingService.renewLink().subscribe({
          next: page => {
            this.page = page;
            this.notifications.success('booking.renewed');
          },
          error: () => this.notifications.error('booking.saveError')
        });
      });
  }

  cancel(call: Booking): void {
    this.confirm.confirm({ titleKey: 'booking.cancelTitle', messageKey: 'booking.cancelMessage', messageParams: { name: call.name },
      confirmKey: 'booking.cancelCall', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.bookingService.cancel(call.id).subscribe({
        next: cancelled => {
          this.calls = this.calls.map(item => item.id === cancelled.id ? cancelled : item);
          this.notifications.success('booking.cancelled');
          this.changed.emit();
        },
        error: () => this.notifications.error('booking.saveError')
      });
    });
  }

  /** The calls still to come, the soonest first. */
  get upcoming(): Booking[] {
    const now = Date.now();
    return this.calls.filter(call => !call.cancelledAt && Date.parse(call.endsAt) > now);
  }

  private loadCalls(): void {
    this.bookingService.list().subscribe({
      next: calls => this.calls = calls,
      error: () => this.notifications.error('booking.loadError')
    });
  }
}
