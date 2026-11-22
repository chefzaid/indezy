import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AiNegotiation } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';

export interface AiNegotiationDialogData {
  projectId: number;
}

/**
 * Prepares the rate negotiation of an opportunity with AI (see docs/features.md#ai-features): the counter-offer, the
 * rate to walk away below, the ESN's margin and the arguments for the call.
 */
@Component({
  selector: 'app-ai-negotiation-dialog',
  imports: [DecimalPipe, MatButtonModule, MatDialogModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'ai.negotiation.title' | translate }}</h2>
    <mat-dialog-content>
      @if (preparing) {
        <div class="busy"><mat-spinner diameter="28" [attr.aria-label]="'ai.negotiation.busy' | translate"></mat-spinner></div>
      } @else if (preparation; as preparation) {
        <dl class="figures" data-ai-negotiation>
          <div><dt>{{ 'ai.negotiation.counter' | translate }}</dt><dd data-ai-negotiation-counter>{{ preparation.counterOffer | number:'1.0-0' }} €</dd></div>
          <div><dt>{{ 'ai.negotiation.walkAway' | translate }}</dt><dd>{{ preparation.walkAway | number:'1.0-0' }} €</dd></div>
          @if (preparation.marginEuros !== null) {
            <div><dt>{{ 'ai.negotiation.margin' | translate }}</dt>
              <dd>{{ preparation.marginEuros | number:'1.0-0' }} € ({{ preparation.marginPercent }} %)</dd></div>
          }
          @if (preparation.marketMedian) {
            <div><dt>{{ 'ai.negotiation.market' | translate }}</dt><dd>{{ preparation.marketMedian | number:'1.0-0' }} €</dd></div>
          }
        </dl>
        @if (preparation.strategy) {
          <p class="strategy">{{ preparation.strategy }}</p>
        }
        @if (preparation.arguments.length) {
          <h3>{{ 'ai.negotiation.arguments' | translate }}</h3>
          <ul>
            @for (argument of preparation.arguments; track $index) {
              <li>{{ argument }}</li>
            }
          </ul>
        }
        <p class="disclaimer">{{ 'ai.disclaimer' | translate }}</p>
      } @else if (errorKey) {
        <p class="error" role="alert">{{ errorKey | translate }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="prepare()" [disabled]="preparing">{{ 'ai.negotiation.again' | translate }}</button>
      <button mat-flat-button color="primary" type="button" mat-dialog-close>{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .busy { display: flex; justify-content: center; padding: 24px; }
    .figures { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin: 0 0 12px; }
    .figures div { padding: 8px 12px; border-radius: 8px; background: var(--mat-sys-surface-container); }
    dt { color: var(--mat-sys-on-surface-variant); font-size: .8125rem; }
    dd { margin: 2px 0 0; font-size: 1.25rem; font-weight: 500; }
    h3 { margin: 12px 0 4px; font-size: 1rem; }
    ul { margin: 0 0 12px; padding-left: 20px; }
    .strategy { margin: 0 0 8px; }
    .disclaimer { margin: 0; color: var(--mat-sys-on-surface-variant); font-size: .8125rem; }
    .error { color: var(--mat-sys-error); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiNegotiationDialogComponent implements OnInit {
  private readonly data = inject<AiNegotiationDialogData>(MAT_DIALOG_DATA);
  private readonly ai = inject(AiService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  preparation: AiNegotiation | null = null;
  preparing = false;
  errorKey: string | null = null;

  ngOnInit(): void {
    this.prepare();
  }

  prepare(): void {
    this.preparing = true;
    this.errorKey = null;
    this.ai.negotiation(this.data.projectId, this.translate.currentLang === 'en' ? 'EN' : 'FR').subscribe({
      next: preparation => {
        this.preparing = false;
        this.preparation = preparation;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.preparing = false;
        this.preparation = null;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }
}
