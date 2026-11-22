import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AiOfferVerdict } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';

/** **Should I apply? (AI)** on a feed offer: one line on whether it fits the profile and its deal-breakers, kept once asked. */
@Component({
  selector: 'app-offer-verdict',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './offer-verdict.component.html',
  styleUrls: ['./offer-verdict.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class OfferVerdictComponent implements OnChanges {
  @Input({ required: true }) offerId!: number;

  private readonly ai = inject(AiService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  verdict: AiOfferVerdict | null = null;
  asking = false;
  errorKey: string | null = null;

  ngOnChanges(): void {
    this.verdict = null;
    this.ai.offerVerdict(this.offerId).subscribe({
      next: verdict => {
        this.verdict = verdict?.verdict ? verdict : null;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  ask(): void {
    if (this.asking) {
      return;
    }
    this.asking = true;
    this.errorKey = null;
    this.ai.triageOffer(this.offerId, this.translate.currentLang === 'en' ? 'EN' : 'FR').subscribe({
      next: verdict => {
        this.verdict = verdict;
        this.asking = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.asking = false;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }
}
