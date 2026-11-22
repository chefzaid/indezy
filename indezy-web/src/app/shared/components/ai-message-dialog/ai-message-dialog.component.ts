import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AI_MESSAGE_KINDS, AiMessageKind, DocumentLanguage } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';

export interface AiMessageDialogData {
  projectId: number;
  /** The kind to start with, as the opportunity's stage suggests. */
  kind?: AiMessageKind;
}

/**
 * Drafts a message about an opportunity with AI, for the freelancer to read, change and copy: an application, a
 * follow-up, a thank-you or a decline, in French or English, with a few words on what to say.
 */
@Component({
  selector: 'app-ai-message-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressSpinnerModule,
    MatSelectModule, TranslateModule],
  templateUrl: './ai-message-dialog.component.html',
  styleUrls: ['./ai-message-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiMessageDialogComponent {
  readonly data = inject<AiMessageDialogData>(MAT_DIALOG_DATA);
  private readonly ai = inject(AiService);
  private readonly fb = inject(FormBuilder);
  private readonly clipboard = inject(Clipboard);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly kinds = AI_MESSAGE_KINDS;
  readonly form = this.fb.nonNullable.group({
    kind: [this.data.kind ?? 'FOLLOW_UP' as AiMessageKind, Validators.required],
    language: [(this.translate.currentLang === 'en' ? 'EN' : 'FR') as DocumentLanguage],
    notes: ['', Validators.maxLength(1000)]
  });
  readonly draft = this.fb.nonNullable.group({ subject: [''], body: [''] });
  drafted = false;
  writing = false;
  errorKey: string | null = null;

  write(): void {
    if (this.writing) {
      return;
    }
    const { kind, language, notes } = this.form.getRawValue();
    this.writing = true;
    this.errorKey = null;
    this.ai.draftMessage(this.data.projectId, kind, language, notes.trim() || null).subscribe({
      next: message => {
        this.draft.setValue({ subject: message.subject, body: message.body });
        this.drafted = true;
        this.writing = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.writing = false;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }

  copy(): void {
    const { subject, body } = this.draft.getRawValue();
    if (this.clipboard.copy(subject ? `${subject}\n\n${body}` : body)) {
      this.notifications.success('ai.message.copied');
    }
  }
}
