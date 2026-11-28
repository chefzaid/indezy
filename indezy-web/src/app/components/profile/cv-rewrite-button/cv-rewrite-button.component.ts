import { ChangeDetectionStrategy, Component, EventEmitter, Output, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { AiCvRewriteDialogComponent } from '../../../shared/components/ai-cv-rewrite-dialog/ai-cv-rewrite-dialog.component';

/** Opens the whole-CV rewrite with AI (see docs/features.md#ai-features); emits once wording was written to the profile. */
@Component({
  selector: 'app-cv-rewrite-button',
  imports: [MatButtonModule, MatDialogModule, MatIconModule, TranslateModule],
  template: `
    <button mat-stroked-button type="button" (click)="open()" data-ai-cv-open>
      <mat-icon>auto_awesome</mat-icon>{{ 'ai.cv.open' | translate }}
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CvRewriteButtonComponent {
  @Output() readonly approved = new EventEmitter<void>();

  private readonly dialog = inject(MatDialog);

  open(): void {
    this.dialog.open(AiCvRewriteDialogComponent, { width: '760px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed().subscribe(approved => {
        if (approved === true) {
          this.approved.emit();
        }
      });
  }
}
