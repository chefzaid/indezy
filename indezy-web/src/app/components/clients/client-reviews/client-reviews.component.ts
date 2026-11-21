import { ChangeDetectionStrategy, Component, Input, OnChanges, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

import { CompanyReview, CompanyReviews } from '../../../models';
import { CapabilitiesService } from '../../../services/admin/capabilities.service';
import { NetworkReviewService } from '../../../services/network-review/network-review.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ReportDialogComponent } from '../../network/report-dialog/report-dialog.component';

export type ReviewRating = 'paymentRating' | 'processRating' | 'rateRating';

/**
 * What the network's members say of a client or an ESN, shown on its page: the averages and the reviews without their
 * authors, each with **Report**, and the member's own review to write once they won a mission with it.
 */
@Component({
  selector: 'app-client-reviews',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatButtonToggleModule, MatCardModule, MatFormFieldModule,
    MatIconModule, MatInputModule, TranslateModule],
  templateUrl: './client-reviews.component.html',
  styleUrls: ['./client-reviews.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ClientReviewsComponent implements OnChanges {
  @Input({ required: true }) clientId!: number;

  private readonly reviewService = inject(NetworkReviewService);
  private readonly capabilities = inject(CapabilitiesService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);

  readonly ratings: ReviewRating[] = ['paymentRating', 'processRating', 'rateRating'];
  readonly scale = [1, 2, 3, 4, 5];
  company: CompanyReviews | null = null;
  /** The reader is not a member of the network: it asks them to join. */
  notMember = false;
  writing = false;
  saving = false;

  readonly form = this.fb.group({
    paymentRating: [null as number | null, Validators.required],
    processRating: [null as number | null, Validators.required],
    rateRating: [null as number | null, Validators.required],
    text: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(2000)]]
  });

  get enabled(): boolean {
    return this.capabilities.snapshot().features.SOCIAL_NETWORK === true;
  }

  ngOnChanges(): void {
    if (!this.enabled) {
      return;
    }
    this.reviewService.company(this.clientId).subscribe({
      next: company => this.show(company),
      error: (error: HttpErrorResponse) => this.notMember = error.status === 409
    });
  }

  average(key: 'paymentAverage' | 'processAverage' | 'rateAverage'): number | null {
    return this.company?.[key] ?? null;
  }

  write(): void {
    const mine = this.company?.mine;
    this.form.reset(mine ? { paymentRating: mine.paymentRating, processRating: mine.processRating, rateRating: mine.rateRating, text: mine.text } :
      { paymentRating: null, processRating: null, rateRating: null, text: '' });
    this.writing = true;
  }

  setRating(rating: ReviewRating, value: number): void {
    this.form.controls[rating].setValue(value);
  }

  save(): void {
    if (this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving = true;
    this.reviewService.write(this.clientId, { paymentRating: value.paymentRating!, processRating: value.processRating!,
      rateRating: value.rateRating!, text: value.text!.trim() }).subscribe({
      next: company => {
        this.saving = false;
        this.writing = false;
        this.show(company);
        this.notifications.success(company.mine?.status === 'HELD' ? 'clients.reviews.held' : 'clients.reviews.published');
      },
      error: () => {
        this.saving = false;
        this.notifications.error('clients.reviews.saveError');
      }
    });
  }

  remove(): void {
    this.confirm.confirm({ messageKey: 'clients.reviews.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.reviewService.delete(this.clientId).subscribe({
        next: () => {
          this.notifications.success('clients.reviews.deleted');
          this.ngOnChanges();
        },
        error: () => this.notifications.error('clients.reviews.saveError')
      });
    });
  }

  report(review: CompanyReview): void {
    ReportDialogComponent.open(this.dialog, { target: 'reviews', id: review.id }).subscribe();
  }

  private show(company: CompanyReviews): void {
    this.company = company;
    this.notMember = false;
  }
}
