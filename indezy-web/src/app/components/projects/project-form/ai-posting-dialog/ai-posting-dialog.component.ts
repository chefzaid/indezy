import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { AiPosting } from '../../../../models';
import { AiService, aiErrorKey } from '../../../../services/ai/ai.service';

/** What the dialog gives back: the posting read, and its text to keep as the description. */
export interface AiPostingResult {
  posting: AiPosting;
  text: string;
}

const TEXT_MAX = 20_000;

/** Reads a job posting pasted as text into the new opportunity's fields, with AI. */
@Component({
  selector: 'app-ai-posting-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './ai-posting-dialog.component.html',
  styleUrls: ['./ai-posting-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiPostingDialogComponent {
  private readonly ai = inject(AiService);
  private readonly dialogRef = inject(MatDialogRef<AiPostingDialogComponent, AiPostingResult>);
  private readonly changes = inject(ChangeDetectorRef);

  readonly max = TEXT_MAX;
  readonly text = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(TEXT_MAX)] });
  reading = false;
  errorKey: string | null = null;

  read(): void {
    const text = this.text.value.trim();
    if (!text || this.text.invalid || this.reading) {
      this.text.markAsTouched();
      return;
    }
    this.reading = true;
    this.errorKey = null;
    this.ai.readPosting(text).subscribe({
      next: posting => this.dialogRef.close({ posting, text }),
      error: (error: HttpErrorResponse) => {
        this.reading = false;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }
}
