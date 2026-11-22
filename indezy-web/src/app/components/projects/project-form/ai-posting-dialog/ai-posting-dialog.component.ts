import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
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
const PDF_MAX_BYTES = 5 * 1024 * 1024;

/** Reads a job posting pasted as text into the new opportunity's fields, with AI. */
@Component({
  selector: 'app-ai-posting-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressSpinnerModule, TranslateModule],
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
  loadingPdf = false;
  /** The PDF's text was cut to fit. */
  pdfCut = false;
  errorKey: string | null = null;

  /** Puts the text of the PDF chosen in the box, to check before reading it. */
  fromPdf(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > PDF_MAX_BYTES) {
      this.errorKey = 'ai.posting.pdfTooLarge';
      return;
    }
    this.loadingPdf = true;
    this.errorKey = null;
    this.ai.postingPdfText(file).subscribe({
      next: ({ text, cut }) => {
        this.loadingPdf = false;
        this.pdfCut = cut;
        this.text.setValue(text);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loadingPdf = false;
        const message: string = error.error?.validationErrors?.file ?? error.error?.message ?? '';
        this.errorKey = message.includes('protected') ? 'ai.posting.pdfProtected' : message.includes('no text') ? 'ai.posting.pdfNoText' :
          'ai.posting.pdfUnreadable';
        this.changes.markForCheck();
      }
    });
  }

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
