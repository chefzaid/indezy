import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { AI_MODELS, AI_TASKS, AiModel, AiStatus } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * Profile → Preferences → **AI**: whether the AI features can be used, this month's allowance on Indezy's key, and the
 * member's own Anthropic key, which lifts the allowance, with the model it uses.
 */
@Component({
  selector: 'app-ai-settings',
  imports: [DecimalPipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule,
    TranslateModule],
  templateUrl: './ai-settings.component.html',
  styleUrls: ['./ai-settings.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiSettingsComponent implements OnInit {
  private readonly ai = inject(AiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly models = AI_MODELS;
  readonly tasks = AI_TASKS;
  status: AiStatus | null = null;
  saving = false;
  readonly form = this.fb.nonNullable.group({
    apiKey: ['', [Validators.required, Validators.pattern(/^sk-ant-[A-Za-z0-9_-]+$/), Validators.maxLength(300)]],
    // Null: each task on its own model.
    model: [null as AiModel | null]
  });

  ngOnInit(): void {
    this.ai.status().subscribe({ next: status => this.show(status), error: () => undefined });
  }

  /** The share of the allowance used, for the bar. */
  get used(): number {
    return this.status && this.status.limitUsd > 0 ? Math.min(100, Math.round(this.status.spentUsd * 100 / this.status.limitUsd)) : 0;
  }

  saveKey(): void {
    if (this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }
    const { apiKey, model } = this.form.getRawValue();
    this.saving = true;
    this.ai.setKey(apiKey.trim(), model).subscribe({
      next: status => {
        this.form.reset({ apiKey: '', model });
        this.show(status);
        this.notifications.success('ai.settings.keySaved');
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.notifications.error(error.status === 400 ? 'ai.settings.keyInvalid' : aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }

  removeKey(): void {
    this.saving = true;
    this.ai.removeKey().subscribe({
      next: status => {
        this.show(status);
        this.notifications.success('ai.settings.keyRemoved');
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.notifications.error(aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }

  private show(status: AiStatus): void {
    this.status = status;
    this.saving = false;
    this.changes.markForCheck();
  }
}
