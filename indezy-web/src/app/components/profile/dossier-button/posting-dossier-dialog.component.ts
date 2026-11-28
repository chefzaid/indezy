import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { DocumentLanguage } from '../../../models';
import { AiService, aiBlobErrorKey, postingPdfErrorKey } from '../../../services/ai/ai.service';
import { saveBlob } from '../../../shared/utils/download-file';
import { PDF_MAX_BYTES, TEXT_MAX } from '../../projects/project-form/ai-posting-dialog/ai-posting-dialog.component';

/**
 * The dossier de compétences tailored with AI to a job posting pasted as text or read from its PDF, without adding it
 * to the pipeline (see docs/features.md#ai-features): nothing is kept, the PDF is downloaded.
 */
@Component({
  selector: 'app-posting-dossier-dialog',
  imports: [FormsModule, ReactiveFormsModule, MatButtonModule, MatButtonToggleModule, MatDialogModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'ai.dossier.posting.title' | translate }}</h2>
    <mat-dialog-content>
      <p class="hint">{{ 'ai.dossier.posting.hint' | translate }}</p>
      <div class="row">
        <input #pdfInput type="file" accept="application/pdf,.pdf" hidden (change)="fromPdf($event)" data-posting-dossier-pdf-input>
        <button mat-stroked-button type="button" (click)="pdfInput.click()" [disabled]="loadingPdf || making" data-posting-dossier-pdf>
          @if (loadingPdf) {
            <mat-spinner diameter="18" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
          } @else {
            <mat-icon>picture_as_pdf</mat-icon>
          }
          {{ 'ai.posting.pdf' | translate }}
        </button>
        @if (pdfCut) {
          <span class="muted" role="status">{{ 'ai.posting.pdfCut' | translate }}</span>
        }
      </div>
      <mat-form-field appearance="outline" class="full">
        <mat-label>{{ 'ai.posting.text' | translate }}</mat-label>
        <textarea matInput [formControl]="text" rows="10" [attr.maxlength]="max" data-posting-dossier-text></textarea>
        <mat-error>{{ 'ai.posting.textError' | translate }}</mat-error>
      </mat-form-field>
      <div class="row">
        <mat-button-toggle-group [(ngModel)]="layout" [attr.aria-label]="'ai.dossier.posting.layout' | translate" data-posting-dossier-layout>
          <mat-button-toggle value="NAMED">{{ 'ai.cv.named' | translate }}</mat-button-toggle>
          <mat-button-toggle value="ANONYMOUS">{{ 'ai.cv.anonymous' | translate }}</mat-button-toggle>
        </mat-button-toggle-group>
        <mat-button-toggle-group [(ngModel)]="language" [attr.aria-label]="'ai.cv.language' | translate" data-posting-dossier-language>
          <mat-button-toggle value="FR">Français</mat-button-toggle>
          <mat-button-toggle value="EN">English</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      @if (errorKey) {
        <p class="error" role="alert" data-ai-error>{{ errorKey | translate }}</p>
      }
      <p class="muted">{{ 'ai.disclaimer' | translate }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.close' | translate }}</button>
      <button mat-flat-button color="primary" type="button" (click)="make()" [disabled]="making" data-posting-dossier-make>
        @if (making) {
          <mat-spinner diameter="18" [attr.aria-label]="'ai.dossier.busy' | translate"></mat-spinner>
        } @else {
          <mat-icon>download</mat-icon>
        }
        {{ 'ai.dossier.posting.make' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .hint, .muted { margin: 0 0 12px; color: var(--mat-sys-on-surface-variant); }
    .muted { font-size: .8125rem; }
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-bottom: 12px; }
    .full { width: 100%; }
    .error { color: var(--mat-sys-error); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PostingDossierDialogComponent {
  private readonly ai = inject(AiService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly max = TEXT_MAX;
  readonly text = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(TEXT_MAX)] });
  layout: 'NAMED' | 'ANONYMOUS' = 'NAMED';
  language: DocumentLanguage = 'FR';
  making = false;
  loadingPdf = false;
  pdfCut = false;
  errorKey: string | null = null;

  /** Puts the text of the PDF chosen in the box, to check before tailoring the dossier to it. */
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
        this.errorKey = postingPdfErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }

  make(): void {
    const text = this.text.value.trim();
    if (!text || this.text.invalid || this.making) {
      this.text.markAsTouched();
      return;
    }
    this.making = true;
    this.errorKey = null;
    this.ai.postingDossier(text, this.layout, this.language).subscribe({
      next: file => {
        this.making = false;
        saveBlob(file, `${this.language === 'EN' ? 'skills-profile' : 'dossier-competences'}${this.layout === 'ANONYMOUS' ? '-anonymous' : ''}-tailored.pdf`);
        this.changes.markForCheck();
      },
      error: (error: unknown) => {
        this.making = false;
        void aiBlobErrorKey(error).then(key => {
          this.errorKey = key;
          this.changes.markForCheck();
        });
        this.changes.markForCheck();
      }
    });
  }
}
