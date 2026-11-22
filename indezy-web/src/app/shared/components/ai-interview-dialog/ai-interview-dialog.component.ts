import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AiInterview } from '../../../models';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';

export interface AiInterviewDialogData {
  projectId: number;
}

/**
 * Prepares the next interview of an opportunity with AI (see docs/features.md#ai-features): likely technical and
 * behavioural questions with hints, a two-minute pitch to copy, questions to ask, kept as a journal note on request.
 */
@Component({
  selector: 'app-ai-interview-dialog',
  imports: [MatButtonModule, MatDialogModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'ai.interview.title' | translate }}</h2>
    <mat-dialog-content>
      @if (preparing) {
        <div class="busy"><mat-spinner diameter="28" [attr.aria-label]="'ai.interview.busy' | translate"></mat-spinner></div>
      } @else if (preparation; as preparation) {
        <section data-ai-interview>
          <h3>{{ 'ai.interview.technical' | translate }}</h3>
          <ul>
            @for (item of preparation.technical; track $index) {
              <li><strong>{{ item.question }}</strong>@if (item.hint) { <span class="hint">{{ item.hint }}</span> }</li>
            }
          </ul>
          <h3>{{ 'ai.interview.behavioural' | translate }}</h3>
          <ul>
            @for (item of preparation.behavioural; track $index) {
              <li><strong>{{ item.question }}</strong>@if (item.hint) { <span class="hint">{{ item.hint }}</span> }</li>
            }
          </ul>
          <h3>{{ 'ai.interview.pitch' | translate }}</h3>
          <p class="pitch" data-ai-interview-pitch>{{ preparation.pitch }}</p>
          <button mat-stroked-button type="button" (click)="copyPitch()"><mat-icon>content_copy</mat-icon>{{ 'ai.interview.copyPitch' | translate }}</button>
          @if (preparation.toAsk.length) {
            <h3>{{ 'ai.interview.toAsk' | translate }}</h3>
            <ul>
              @for (question of preparation.toAsk; track $index) {
                <li>{{ question }}</li>
              }
            </ul>
          }
          <p class="disclaimer">{{ 'ai.disclaimer' | translate }}</p>
        </section>
      } @else if (errorKey) {
        <p class="error" role="alert">{{ errorKey | translate }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="prepare()" [disabled]="preparing">{{ 'ai.interview.again' | translate }}</button>
      <button mat-stroked-button type="button" (click)="keepAsNote()" [disabled]="!preparation || keeping" data-ai-interview-note>
        {{ 'ai.interview.keep' | translate }}
      </button>
      <button mat-flat-button color="primary" type="button" mat-dialog-close>{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .busy { display: flex; justify-content: center; padding: 24px; }
    h3 { margin: 16px 0 4px; font-size: 1rem; }
    h3:first-child { margin-top: 0; }
    ul { margin: 0 0 8px; padding-left: 20px; }
    li { margin-bottom: 6px; }
    .hint { display: block; color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
    .pitch { white-space: pre-line; margin: 0 0 8px; }
    .disclaimer { margin: 12px 0 0; color: var(--mat-sys-on-surface-variant); font-size: .8125rem; }
    .error { color: var(--mat-sys-error); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiInterviewDialogComponent implements OnInit {
  private readonly data = inject<AiInterviewDialogData>(MAT_DIALOG_DATA);
  private readonly ai = inject(AiService);
  private readonly projects = inject(ProjectService);
  private readonly notifications = inject(NotificationService);
  private readonly clipboard = inject(Clipboard);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  preparation: AiInterview | null = null;
  preparing = false;
  keeping = false;
  errorKey: string | null = null;

  ngOnInit(): void {
    this.prepare();
  }

  prepare(): void {
    this.preparing = true;
    this.errorKey = null;
    this.ai.interview(this.data.projectId, this.translate.currentLang === 'en' ? 'EN' : 'FR').subscribe({
      next: preparation => {
        this.preparing = false;
        this.preparation = preparation;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.preparing = false;
        this.preparation = null;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }

  copyPitch(): void {
    if (this.preparation && this.clipboard.copy(this.preparation.pitch)) {
      this.notifications.success('ai.interview.copied');
    }
  }

  /** Keeps the preparation as a Markdown note in the opportunity's journal. */
  keepAsNote(): void {
    const preparation = this.preparation;
    if (!preparation || this.keeping) {
      return;
    }
    const t = (key: string): string => this.translate.instant(`ai.interview.${key}`);
    const questions = (items: { question: string; hint: string }[]): string =>
      items.map(item => `- **${item.question}**${item.hint ? ` ${item.hint}` : ''}`).join('\n');
    const note = [`## ${t('title')}`, `### ${t('technical')}`, questions(preparation.technical), `### ${t('behavioural')}`,
      questions(preparation.behavioural), `### ${t('pitch')}`, preparation.pitch,
      ...(preparation.toAsk.length ? [`### ${t('toAsk')}`, preparation.toAsk.map(question => `- ${question}`).join('\n')] : [])].join('\n\n');
    this.keeping = true;
    this.projects.addProjectNote(this.data.projectId, note).subscribe({
      next: () => {
        this.keeping = false;
        this.notifications.success('ai.interview.kept');
        this.changes.markForCheck();
      },
      error: () => {
        this.keeping = false;
        this.notifications.error('ai.interview.keepError');
        this.changes.markForCheck();
      }
    });
  }
}
