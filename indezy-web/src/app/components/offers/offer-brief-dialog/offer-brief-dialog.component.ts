import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { AiOfferBrief, OfferFilters, emptyOfferFilters } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';

/** What the dialog gives back: the feed's filters read, what to avoid among them as words to leave out. */
export interface OfferBriefResult {
  filters: OfferFilters;
  avoid: string[];
}

const TEXT_MAX = 2_000;

/** Reads what the freelancer looks for, said in their own words, into the feed's filters with AI. */
@Component({
  selector: 'app-offer-brief-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'ai.brief.title' | translate }}</h2>
    <mat-dialog-content>
      <p class="hint">{{ 'ai.brief.hint' | translate }}</p>
      <mat-form-field appearance="outline" class="full">
        <mat-label>{{ 'ai.brief.text' | translate }}</mat-label>
        <textarea matInput [formControl]="text" rows="5" [attr.maxlength]="max" [placeholder]="'ai.brief.placeholder' | translate"
          data-offer-brief-text></textarea>
      </mat-form-field>
      @if (errorKey) {
        <p class="error" role="alert">{{ errorKey | translate }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button color="primary" type="button" (click)="read()" [disabled]="reading || !text.value.trim()" data-offer-brief-read>
        @if (reading) {
          <mat-spinner diameter="18" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
        }
        {{ 'ai.brief.read' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .hint { margin: 0 0 12px; color: var(--mat-sys-on-surface-variant); }
    .full { width: 100%; min-width: min(520px, 80vw); }
    .error { color: var(--mat-sys-error); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class OfferBriefDialogComponent {
  private readonly ai = inject(AiService);
  private readonly dialogRef = inject<MatDialogRef<OfferBriefDialogComponent, OfferBriefResult>>(MatDialogRef);
  private readonly changes = inject(ChangeDetectorRef);

  readonly max = TEXT_MAX;
  readonly text = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(TEXT_MAX)] });
  reading = false;
  errorKey: string | null = null;

  read(): void {
    const text = this.text.value.trim();
    if (!text || this.reading) {
      return;
    }
    this.reading = true;
    this.errorKey = null;
    this.ai.offerBrief(text).subscribe({
      next: brief => this.dialogRef.close({ filters: toFilters(brief), avoid: brief.avoid }),
      error: (error: HttpErrorResponse) => {
        this.reading = false;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }
}

/** The feed's filters for a brief, every other filter cleared. */
export function toFilters(brief: AiOfferBrief): OfferFilters {
  return {
    ...emptyOfferFilters(),
    skills: brief.skills,
    rateMin: brief.rateMin ?? undefined,
    minRemoteDays: brief.minRemoteDays ?? undefined,
    workModes: brief.workModes,
    regions: brief.regions,
    startBefore: brief.startBefore ?? undefined,
    durationMin: brief.durationMin ?? undefined,
    q: brief.q ?? undefined,
    exclude: brief.avoid.length ? brief.avoid.join(' ') : undefined
  };
}
