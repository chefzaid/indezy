import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AiChatTurn } from '../../../../models';
import { AiService, aiErrorKey } from '../../../../services/ai/ai.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const QUESTION_MAX = 2_000;

/**
 * The AI conversation about an opportunity, on its page: interview preparation, negotiation, a post-mortem, the
 * opportunity's description, notes, steps and journal as context; the history stays with the opportunity.
 */
@Component({
  selector: 'app-ai-chat',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressSpinnerModule,
    TranslateModule],
  templateUrl: './ai-chat.component.html',
  styleUrls: ['./ai-chat.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiChatComponent implements OnChanges {
  @Input({ required: true }) projectId!: number;

  private readonly ai = inject(AiService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);

  readonly max = QUESTION_MAX;
  readonly suggestions = ['interview', 'negotiation', 'postMortem'] as const;
  readonly question = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(QUESTION_MAX)] });
  turns: AiChatTurn[] = [];
  open = false;
  asking = false;
  errorKey: string | null = null;

  ngOnChanges(): void {
    this.ai.chatHistory(this.projectId).subscribe({
      next: turns => {
        this.turns = turns;
        // A conversation already started opens.
        this.open = this.open || turns.length > 0;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  start(): void {
    this.open = true;
    this.changes.markForCheck();
  }

  /** Fills a suggested question in, for the freelancer to send or change. */
  suggest(suggestion: typeof this.suggestions[number]): void {
    this.question.setValue(this.translate.instant(`ai.chat.suggestions.${suggestion}`));
  }

  ask(): void {
    const text = this.question.value.trim();
    if (!text || this.question.invalid || this.asking) {
      return;
    }
    this.asking = true;
    this.errorKey = null;
    this.ai.ask(this.projectId, text).subscribe({
      next: turns => {
        this.turns = turns;
        this.question.reset();
        this.asking = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.asking = false;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }

  clear(): void {
    this.confirm.confirm({ messageKey: 'ai.chat.clearConfirm', confirmKey: 'ai.chat.clear', danger: true }).subscribe(confirmed => {
      if (confirmed) {
        this.ai.clearChat(this.projectId).subscribe(() => {
          this.turns = [];
          this.changes.markForCheck();
        });
      }
    });
  }
}
