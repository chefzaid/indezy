import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AiRateSuggestion } from '../../../../models';
import { AiService, aiErrorKey } from '../../../../services/ai/ai.service';
import { NotificationService } from '../../../../services/notification/notification.service';

/**
 * Suggests the daily rate to ask for the opportunity being written (see docs/features.md#ai-features), from what the
 * form says of it, the profile and the market; **Use** puts it in the daily rate field.
 */
@Component({
  selector: 'app-ai-rate-suggestion',
  imports: [DecimalPipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <div class="ai-rate" data-ai-rate>
      <button mat-stroked-button type="button" (click)="suggest()" [disabled]="busy" data-ai-rate-ask>
        @if (busy) {
          <mat-progress-spinner mode="indeterminate" diameter="18" [attr.aria-label]="'ai.rate.busy' | translate"></mat-progress-spinner>
        } @else {
          <mat-icon>auto_awesome</mat-icon>
        }
        {{ 'ai.rate.ask' | translate }}
      </button>
      <div role="status" aria-live="polite">
        @if (suggestion; as suggestion) {
          <div class="suggestion" data-ai-rate-suggestion>
            <p class="figure">
              {{ 'ai.rate.figure' | translate:{ rate: (suggestion.rate | number:'1.0-0'), low: (suggestion.low | number:'1.0-0'),
                high: (suggestion.high | number:'1.0-0') } }}
              @if (suggestion.marketMedian) {
                <span class="market">{{ 'ai.rate.market' | translate:{ median: (suggestion.marketMedian | number:'1.0-0') } }}</span>
              }
            </p>
            @if (suggestion.reasons.length) {
              <ul>
                @for (reason of suggestion.reasons; track $index) {
                  <li>{{ reason }}</li>
                }
              </ul>
            }
            <p class="disclaimer">{{ 'ai.disclaimer' | translate }}</p>
            <button mat-flat-button color="primary" type="button" (click)="use.emit(suggestion.rate)" data-ai-rate-use>
              {{ 'ai.rate.use' | translate:{ rate: (suggestion.rate | number:'1.0-0') } }}
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .ai-rate { display: flex; flex-direction: column; gap: 8px; margin: 0 0 16px; }
    .ai-rate > button { align-self: flex-start; }
    .suggestion { padding: 12px; border-radius: 8px; background: var(--mat-sys-surface-container); }
    .figure { margin: 0 0 4px; font-weight: 500; }
    .market { display: block; font-weight: 400; color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
    ul { margin: 4px 0 8px; padding-left: 20px; }
    .disclaimer { margin: 0 0 8px; color: var(--mat-sys-on-surface-variant); font-size: .8125rem; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiRateSuggestionComponent {
  /** The opportunity form, read when asked. */
  @Input({ required: true }) form!: FormGroup;
  /** The rate to put in the daily rate field. */
  @Output() readonly use = new EventEmitter<number>();

  private readonly ai = inject(AiService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly notifications = inject(NotificationService);

  suggestion: AiRateSuggestion | null = null;
  busy = false;

  suggest(): void {
    const value = this.form.getRawValue() as Record<string, unknown>;
    const text = (key: string): string | null => typeof value[key] === 'string' && (value[key] as string).trim() ? (value[key] as string).trim() : null;
    const number = (key: string): number | null => value[key] === '' || value[key] === null || value[key] === undefined ||
      Number.isNaN(Number(value[key])) ? null : Number(value[key]);
    this.busy = true;
    this.ai.suggestRate({ role: text('role'), techStack: text('techStack'), workMode: (value['workMode'] as never) || null,
      remoteDaysPerMonth: number('remoteDaysPerMonth'), place: text('workAddress'), durationInMonths: number('durationInMonths'),
      description: text('description')?.slice(0, 4000) ?? null, language: this.translate.currentLang === 'en' ? 'EN' : 'FR' }).subscribe({
      next: suggestion => {
        this.busy = false;
        this.suggestion = suggestion;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.busy = false;
        this.notifications.error(aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }
}
