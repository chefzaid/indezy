import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { YearlyReview } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { YearlyReviewService } from '../../services/review/yearly-review.service';
import { LoadingComponent } from '../../shared/components/loading/loading.component';
import { saveBlob } from '../../shared/utils/download-file';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/** The first year a review can be asked for, as the server accepts. */
export const FIRST_REVIEW_YEAR = 2000;

/**
 * A freelancer's year in review: the missions won and worked in it, the revenue they brought against the year before,
 * the opportunities opened and how they ended, the skills most asked for and the network built, exportable as a PDF in
 * the language of the interface.
 */
@Component({
  selector: 'app-yearly-review',
  imports: [PageHeaderComponent, 
    CommonModule, RouterModule, MatButtonModule, MatCardModule, MatChipsModule, MatIconModule, MatProgressSpinnerModule,
    TranslateModule, LoadingComponent
  ],
  templateUrl: './yearly-review.component.html',
  styleUrls: ['./yearly-review.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class YearlyReviewComponent implements OnInit, OnDestroy {
  readonly currentYear = new Date().getFullYear();
  readonly firstYear = FIRST_REVIEW_YEAR;
  year = this.currentYear;
  review?: YearlyReview;
  isLoading = false;
  isDownloading = false;

  private freelanceId?: number;
  private readonly destroy$ = new Subject<void>();
  private readonly cancel$ = new Subject<void>();

  constructor(
    private readonly reviews: YearlyReviewService,
    private readonly auth: AuthService,
    private readonly translate: TranslateService,
    private readonly notifications: NotificationService
  ) {}

  ngOnInit(): void {
    this.freelanceId = this.auth.getUser()?.id;
    this.load();
  }

  ngOnDestroy(): void {
    this.cancel$.next();
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Moves to the year before or after, never past the current one. */
  changeYear(step: number): void {
    const year = this.year + step;
    if (year < FIRST_REVIEW_YEAR || year > this.currentYear) {
      return;
    }
    this.year = year;
    this.load();
  }

  /** Revenue change on the year before, in whole percent; undefined when there was nothing to compare with. */
  get revenueChange(): number | undefined {
    const before = this.review?.previousYear.revenue ?? 0;
    return this.review && before > 0 ? Math.round((this.review.totals.revenue - before) * 100 / before) : undefined;
  }

  get hasEstimates(): boolean {
    return !!this.review?.missions.some(mission => !mission.reported);
  }

  /** Whether the year holds anything at all to review. */
  get isEmpty(): boolean {
    const review = this.review;
    if (!review) {
      return false;
    }
    const network = review.network;
    return review.missions.length === 0 && review.pipeline.opened === 0 &&
      network.clients + network.esns + network.contacts + network.exchanges === 0;
  }

  downloadPdf(): void {
    if (!this.freelanceId || this.isDownloading) {
      return;
    }
    const language = this.translate.getCurrentLang() === 'en' ? 'EN' : 'FR';
    const year = this.year;
    this.isDownloading = true;
    this.reviews.downloadPdf(this.freelanceId, year, language).pipe(takeUntil(this.destroy$)).subscribe({
      next: blob => {
        this.isDownloading = false;
        saveBlob(blob, language === 'EN' ? `indezy-year-in-review-${year}.pdf` : `indezy-bilan-${year}.pdf`);
      },
      error: () => {
        this.isDownloading = false;
        this.notifications.error('review.pdfError');
      }
    });
  }

  private load(): void {
    if (!this.freelanceId) {
      return;
    }
    // A year asked for before the previous one answered must not be overwritten by it.
    this.cancel$.next();
    this.isLoading = true;
    this.reviews.get(this.freelanceId, this.year).pipe(takeUntil(this.cancel$), takeUntil(this.destroy$)).subscribe({
      next: review => {
        this.review = review;
        this.isLoading = false;
      },
      error: () => {
        this.review = undefined;
        this.isLoading = false;
        this.notifications.error('review.loadError');
      }
    });
  }
}
