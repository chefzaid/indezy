import { ChangeDetectionStrategy, Component, Inject, OnDestroy } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

export interface AttachmentPreviewData {
  fileName: string;
  contentType: string;
  /** The file's content, read with the session's credentials. */
  blob: Blob;
}

/** Shows a PDF or an image kept with an opportunity in the page, from its content already read. */
@Component({
  selector: 'app-attachment-preview-dialog',
  imports: [MatDialogModule, MatButtonModule, TranslateModule],
  template: `
    <h2 mat-dialog-title class="preview-title">{{ data.fileName }}</h2>
    <mat-dialog-content class="preview-content">
      @if (isImage) {
        <img [src]="url" [alt]="data.fileName">
      } @else {
        <iframe [src]="url" [title]="data.fileName"></iframe>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'projects.attachments.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    .preview-title { overflow-wrap: anywhere; }
    .preview-content { display: flex; justify-content: center; max-height: 80vh; }
    img { max-width: 100%; max-height: 75vh; object-fit: contain; }
    iframe { width: min(900px, 85vw); height: 75vh; border: 0; }
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AttachmentPreviewDialogComponent implements OnDestroy {
  readonly isImage: boolean;
  readonly url: SafeResourceUrl;
  private readonly objectUrl: string;

  constructor(@Inject(MAT_DIALOG_DATA) readonly data: AttachmentPreviewData, sanitizer: DomSanitizer) {
    this.isImage = data.contentType.startsWith('image/');
    // A blob URL of the file the server typed from its content: PDF or image, never HTML or script.
    this.objectUrl = globalThis.URL.createObjectURL(new Blob([data.blob], { type: data.contentType }));
    this.url = sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl);
  }

  ngOnDestroy(): void {
    globalThis.URL.revokeObjectURL(this.objectUrl);
  }
}
