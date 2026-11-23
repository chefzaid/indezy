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
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';

import { AI_MODELS, AI_TASKS, AiModel, AiStatus, AiTask } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';

/**
 * Profile → Preferences → **AI**: whether the AI features can be used, this month's allowance on Indezy's key, and the
 * member's own Anthropic key, which lifts the allowance, with the model it uses.
 */
@Component({
  selector: 'app-ai-settings',
  imports: [DecimalPipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, TranslateModule],
  templateUrl: './ai-settings.component.html',
  styleUrls: ['./ai-settings.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiSettingsComponent implements OnInit {
  private readonly ai = inject(AiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly users = inject(UserManagementService);

  readonly models = AI_MODELS;
  readonly tasks = AI_TASKS;
  status: AiStatus | null = null;
  /** Whether the daily autopilot run triages the new offers overnight; null until read. */
  autopilot: boolean | null = null;
  saving = false;
  readonly form = this.fb.nonNullable.group({
    apiKey: ['', [Validators.required, Validators.pattern(/^sk-ant-[A-Za-z0-9_-]+$/), Validators.maxLength(300)]],
    // Null: each task on its own model.
    model: [null as AiModel | null]
  });

  ngOnInit(): void {
    this.ai.status().subscribe({ next: status => this.show(status), error: () => undefined });
    this.users.getUserPreferences().subscribe({
      next: preferences => {
        this.autopilot = preferences.autopilot === true;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** Turns the daily autopilot run on or off; failing, the switch goes back. */
  setAutopilot(event: MatSlideToggleChange): void {
    const on = event.checked;
    this.users.updateUserPreferences({ autopilot: on }).subscribe({
      next: preferences => {
        this.autopilot = preferences.autopilot === true;
        this.notifications.success(on ? 'ai.settings.autopilotOn' : 'ai.settings.autopilotOff');
        this.changes.markForCheck();
      },
      error: () => {
        event.source.checked = !on;
        this.notifications.error('ai.settings.autopilotError');
        this.changes.markForCheck();
      }
    });
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

  /** Uses {@code model} for the task with the member's own key; null brings back the key's model or the task's own. */
  chooseModel(task: AiTask, model: AiModel | null): void {
    const chosen: Partial<Record<AiTask, AiModel>> = { ...(this.status?.chosen ?? {}) };
    if (model) {
      chosen[task] = model;
    } else {
      delete chosen[task];
    }
    this.saving = true;
    this.ai.setTaskModels(chosen).subscribe({
      next: status => {
        this.show(status);
        this.notifications.success('ai.settings.taskModelSaved');
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.notifications.error(aiErrorKey(error));
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
