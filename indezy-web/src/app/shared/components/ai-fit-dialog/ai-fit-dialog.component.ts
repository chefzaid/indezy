import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AiFit, DocumentLanguage } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';

export interface AiFitDialogData {
  projectId: number;
}

/**
 * Weighs an opportunity against the profile with AI: a score, a verdict, the pros and cons, the soft criteria to check,
 * the questions to ask and what to think of the rate, from the opportunity, the profile and what matters to the freelancer.
 */
@Component({
  selector: 'app-ai-fit-dialog',
  imports: [DecimalPipe, ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule],
  templateUrl: './ai-fit-dialog.component.html',
  styleUrls: ['./ai-fit-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiFitDialogComponent {
  readonly data = inject<AiFitDialogData>(MAT_DIALOG_DATA);
  private readonly ai = inject(AiService);
  private readonly fb = inject(FormBuilder);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly form = this.fb.nonNullable.group({ priorities: ['', Validators.maxLength(1000)] });
  fit: AiFit | null = null;
  weighing = false;
  errorKey: string | null = null;

  /** The groups of points, in the order they read. */
  readonly groups: { key: 'pros' | 'cons' | 'softCriteria' | 'questions'; icon: string }[] = [
    { key: 'pros', icon: 'thumb_up' }, { key: 'cons', icon: 'thumb_down' }, { key: 'softCriteria', icon: 'groups' },
    { key: 'questions', icon: 'help' }];

  weigh(): void {
    if (this.weighing) {
      return;
    }
    const language: DocumentLanguage = this.translate.currentLang === 'en' ? 'EN' : 'FR';
    this.weighing = true;
    this.errorKey = null;
    this.ai.weigh(this.data.projectId, language, this.form.getRawValue().priorities.trim() || null).subscribe({
      next: fit => {
        this.fit = fit;
        this.weighing = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.weighing = false;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }

  /** The score's band, for its colour: good from 70, mixed from 40. */
  band(score: number): 'good' | 'mixed' | 'poor' {
    return score >= 70 ? 'good' : score >= 40 ? 'mixed' : 'poor';
  }
}
