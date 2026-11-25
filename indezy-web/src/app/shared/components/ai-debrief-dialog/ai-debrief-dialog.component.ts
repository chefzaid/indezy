import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AiDebrief, ProjectDto, ProjectStatus, StepStatus } from '../../../models';
import { ProjectEmailDialogComponent } from '../../../components/projects/project-email/project-email-dialog.component';
import { AiService, aiErrorKey } from '../../../services/ai/ai.service';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { DictationService } from '../../services/dictation.service';

/** The longest debrief read, as the server reads it. */
export const DEBRIEF_MAX = 4000;

export interface AiDebriefDialogData {
  projectId: number;
  /** The opportunity, to write the thank-you to one of its contacts. */
  project?: ProjectDto;
  /** The opportunity's stage, for the move the outcome suggests. */
  status?: ProjectStatus;
}

/** The stage an outcome moves the opportunity to, from the stages it may come from. */
const MOVES: Record<AiDebrief['outcome'], { to: ProjectStatus; from: ProjectStatus[] } | null> = {
  NEXT_ROUND: { to: ProjectStatus.INTERVIEW, from: [ProjectStatus.CONTACT] },
  OFFER: { to: ProjectStatus.OFFER, from: [ProjectStatus.CONTACT, ProjectStatus.INTERVIEW] },
  REJECTED: { to: ProjectStatus.LOST, from: [ProjectStatus.CONTACT, ProjectStatus.INTERVIEW, ProjectStatus.OFFER] },
  WAITING: null
};

/**
 * Turns the debrief of an interview into what comes next with AI (see docs/features.md#ai-features): its likely
 * outcome, the next round added as a step to plan when one was announced, the next steps, and a thank-you message to
 * copy, and the opportunity moved to the stage the outcome points to; kept as a journal note on request. Closes with true
 * once a step was added or the stage changed, for the opportunity to be read again.
 */
@Component({
  selector: 'app-ai-debrief-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'ai.debrief.title' | translate }}</h2>
    <mat-dialog-content>
      @if (reading) {
        <div class="busy"><mat-spinner diameter="28" [attr.aria-label]="'ai.debrief.busy' | translate"></mat-spinner></div>
      } @else if (debrief; as debrief) {
        <section data-ai-debrief>
          <p class="outcome" [attr.data-ai-debrief-outcome]="debrief.outcome">
            <mat-icon aria-hidden="true">{{ icons[debrief.outcome] }}</mat-icon>{{ 'ai.debrief.outcomes.' + debrief.outcome | translate }}
            @if (debrief.reason) {
              <span class="reason" data-ai-debrief-reason>· {{ 'projects.lostReason.' + debrief.reason | translate }}</span>
            }
          </p>
          @if (move(); as to) {
            <div class="round">
              <button mat-stroked-button type="button" (click)="moveTo(to)" [disabled]="moving || moved" data-ai-debrief-move>
                <mat-icon>{{ moved ? 'check' : 'swap_horiz' }}</mat-icon>
                {{ (moved ? 'ai.debrief.moved' : 'dashboard.suggestions.move') | translate:{ status: ('projects.statuses.' + to | translate) } }}
              </button>
            </div>
          }
          @if (debrief.nextRound; as round) {
            <div class="round">
              <span>{{ 'ai.debrief.nextRound' | translate:{ round } }}</span>
              <button mat-stroked-button type="button" (click)="addRound(round)" [disabled]="roundAdded || adding" data-ai-debrief-round>
                <mat-icon>event</mat-icon>{{ (roundAdded ? 'ai.debrief.roundAdded' : 'ai.debrief.addRound') | translate }}
              </button>
            </div>
          }
          @if (debrief.nextSteps.length) {
            <h3>{{ 'ai.debrief.nextSteps' | translate }}</h3>
            <ul data-ai-debrief-steps>
              @for (step of debrief.nextSteps; track $index) {
                <li>{{ step }}</li>
              }
            </ul>
          }
          @if (debrief.thankYouBody) {
            <h3>{{ 'ai.debrief.thankYou' | translate }}</h3>
            <p class="subject"><strong>{{ debrief.thankYouSubject }}</strong></p>
            <p class="body" data-ai-debrief-thanks>{{ debrief.thankYouBody }}</p>
            <div class="thanks-actions">
              @if (data.project) {
                <button mat-stroked-button type="button" (click)="writeThanks()" data-ai-debrief-write>
                  <mat-icon>mail</mat-icon>{{ 'ai.debrief.write' | translate }}
                </button>
              }
              <button mat-stroked-button type="button" (click)="copyThanks()" data-ai-debrief-copy>
                <mat-icon>content_copy</mat-icon>{{ 'ai.debrief.copy' | translate }}
              </button>
            </div>
          }
          <p class="disclaimer">{{ 'ai.disclaimer' | translate }}</p>
        </section>
      } @else {
        <p class="intro">{{ 'ai.debrief.intro' | translate }}</p>
        <mat-form-field appearance="outline" class="notes">
          <mat-label>{{ 'ai.debrief.notes' | translate }}</mat-label>
          <textarea matInput [formControl]="notes" rows="7" [maxlength]="max" data-ai-debrief-notes></textarea>
          <mat-hint align="end">{{ notes.value.length }} / {{ max }}</mat-hint>
        </mat-form-field>
        @if (dictating) {
          <p class="hearing" role="status" data-ai-debrief-hearing>
            <mat-icon aria-hidden="true">graphic_eq</mat-icon>{{ hearing || ('projects.notes.listening' | translate) }}
          </p>
        }
        @if (canDictate) {
          <button mat-stroked-button type="button" (click)="toggleDictation()" [attr.aria-pressed]="dictating" data-ai-debrief-dictate>
            <mat-icon>{{ dictating ? 'stop_circle' : 'mic' }}</mat-icon>
            {{ (dictating ? 'projects.notes.stopDictation' : 'projects.notes.dictate') | translate }}
          </button>
        }
        @if (errorKey) {
          <p class="error" role="alert">{{ errorKey | translate }}</p>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (debrief) {
        <button mat-button type="button" (click)="edit()" data-ai-debrief-edit>{{ 'ai.debrief.edit' | translate }}</button>
        <button mat-stroked-button type="button" (click)="keepAsNote()" [disabled]="keeping" data-ai-debrief-note>
          {{ 'ai.debrief.keep' | translate }}
        </button>
      } @else {
        <button mat-flat-button color="primary" type="button" (click)="read()" [disabled]="notes.invalid || reading" data-ai-debrief-read>
          {{ 'ai.debrief.read' | translate }}
        </button>
      }
      <button mat-button type="button" (click)="close()">{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .busy { display: flex; justify-content: center; padding: 24px; }
    .notes { width: 100%; }
    .hearing { display: flex; align-items: center; gap: 6px; margin: 0 0 8px; color: var(--mat-sys-on-surface-variant); font-style: italic; }
    .intro { margin: 0 0 12px; color: var(--mat-sys-on-surface-variant); }
    .outcome { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 0 0 8px; font-weight: 500; }
    .reason { font-weight: 400; color: var(--mat-sys-on-surface-variant); }
    .round { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-bottom: 8px; }
    h3 { margin: 16px 0 4px; font-size: 1rem; }
    ul { margin: 0 0 8px; padding-left: 20px; }
    li { margin-bottom: 4px; }
    .subject { margin: 0 0 4px; }
    .body { white-space: pre-line; margin: 0 0 8px; }
    .thanks-actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .disclaimer { margin: 12px 0 0; color: var(--mat-sys-on-surface-variant); font-size: .8125rem; }
    .error { color: var(--mat-sys-error); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiDebriefDialogComponent implements OnDestroy {
  readonly data = inject<AiDebriefDialogData>(MAT_DIALOG_DATA);
  private readonly dialog = inject(MatDialog);
  private readonly dialogRef = inject<MatDialogRef<AiDebriefDialogComponent, boolean>>(MatDialogRef);
  private readonly ai = inject(AiService);
  private readonly steps = inject(InterviewStepService);
  private readonly projects = inject(ProjectService);
  private readonly notifications = inject(NotificationService);
  private readonly clipboard = inject(Clipboard);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly dictation = inject(DictationService);

  readonly max = DEBRIEF_MAX;
  readonly icons: Record<AiDebrief['outcome'], string> = { NEXT_ROUND: 'event_upcoming', OFFER: 'handshake', REJECTED: 'block', WAITING: 'hourglass_empty' };
  readonly notes = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(DEBRIEF_MAX)] });
  debrief: AiDebrief | null = null;
  reading = false;
  adding = false;
  roundAdded = false;
  keeping = false;
  moving = false;
  moved = false;
  errorKey: string | null = null;
  readonly canDictate = this.dictation.supported();
  /** What the dictation is still hearing, shown under the debrief until it is final. */
  hearing = '';

  get dictating(): boolean {
    return this.dictation.listening();
  }

  ngOnDestroy(): void {
    this.dictation.stop();
  }

  /** Starts or stops dictating the debrief, in the interface language; the words heard are added to it, within its length. */
  toggleDictation(): void {
    if (this.dictation.listening()) {
      this.dictation.stop();
      return;
    }
    this.dictation.start(this.translate.currentLang, {
      final: text => {
        if (text) {
          const written = this.notes.value.trim();
          this.notes.setValue((written ? `${written} ${text}` : text).slice(0, DEBRIEF_MAX));
          this.changes.markForCheck();
        }
      },
      interim: text => {
        this.hearing = text;
        this.changes.markForCheck();
      },
      ended: error => {
        this.hearing = '';
        this.changes.markForCheck();
        if (error === 'not-allowed' || error === 'service-not-allowed') {
          this.notifications.error('projects.notes.dictationDenied');
        } else if (error && error !== 'no-speech' && error !== 'aborted') {
          this.notifications.error('projects.notes.dictationFailed');
        }
      }
    });
  }

  read(): void {
    if (this.notes.invalid || this.reading) {
      return;
    }
    this.dictation.stop();
    this.reading = true;
    this.errorKey = null;
    this.ai.debrief(this.data.projectId, this.notes.value.trim(), this.translate.currentLang === 'en' ? 'EN' : 'FR').subscribe({
      next: debrief => {
        this.reading = false;
        this.debrief = debrief;
        this.roundAdded = false;
        this.moved = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.reading = false;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }

  /** Back to the debrief, to change it and read it again. */
  edit(): void {
    this.debrief = null;
  }

  /** The stage the outcome moves the opportunity to, when it is at one it may come from. */
  move(): ProjectStatus | null {
    const move = this.debrief ? MOVES[this.debrief.outcome] : null;
    const status = this.data.status;
    return move && status && move.from.includes(status) ? move.to : null;
  }

  /** Moves the opportunity to the stage the outcome points to; turned down, it is abandoned for the reason guessed. */
  moveTo(status: ProjectStatus): void {
    if (this.moving || this.moved) {
      return;
    }
    this.moving = true;
    const reason = status === ProjectStatus.LOST ? this.debrief?.reason ?? undefined : undefined;
    this.projects.updateStatus(this.data.projectId, status, reason ? { lostReason: reason } : undefined).subscribe({
      next: () => {
        this.moving = false;
        this.moved = true;
        this.notifications.success('dashboard.suggestions.applied');
        this.changes.markForCheck();
      },
      error: () => {
        this.moving = false;
        this.notifications.error('dashboard.suggestions.error');
        this.changes.markForCheck();
      }
    });
  }

  /** Adds the next round announced as a step to plan. */
  addRound(round: string): void {
    if (this.adding || this.roundAdded) {
      return;
    }
    this.adding = true;
    this.steps.create({ projectId: this.data.projectId, title: round, status: StepStatus.TO_PLAN }).subscribe({
      next: () => {
        this.adding = false;
        this.roundAdded = true;
        this.notifications.success('ai.debrief.roundAddedToast');
        this.changes.markForCheck();
      },
      error: () => {
        this.adding = false;
        this.notifications.error('ai.debrief.roundError');
        this.changes.markForCheck();
      }
    });
  }

  /** Opens the opportunity's email, the thank-you as its message, to send to one of its contacts or from the mail app. */
  writeThanks(): void {
    const debrief = this.debrief;
    if (debrief && this.data.project) {
      this.dialog.open(ProjectEmailDialogComponent, { data: { ...this.data.project, draft: { subject: debrief.thankYouSubject, body: debrief.thankYouBody } },
        autoFocus: 'first-tabbable', maxWidth: '95vw' });
    }
  }

  copyThanks(): void {
    const debrief = this.debrief;
    if (debrief && this.clipboard.copy(`${debrief.thankYouSubject}\n\n${debrief.thankYouBody}`)) {
      this.notifications.success('ai.debrief.copied');
    }
  }

  /** Keeps the debrief and what comes of it as a Markdown note in the opportunity's journal. */
  keepAsNote(): void {
    const debrief = this.debrief;
    if (!debrief || this.keeping) {
      return;
    }
    const t = (key: string, params?: Record<string, unknown>): string => this.translate.instant(`ai.debrief.${key}`, params);
    const note = [`## ${t('title')}`, this.notes.value.trim(), `**${t('outcomes.' + debrief.outcome)}**`,
      ...(debrief.nextRound ? [t('nextRound', { round: debrief.nextRound })] : []),
      ...(debrief.nextSteps.length ? [`### ${t('nextSteps')}`, debrief.nextSteps.map(step => `- [ ] ${step}`).join('\n')] : []),
      ...(debrief.thankYouBody ? [`### ${t('thankYou')}`, `**${debrief.thankYouSubject}**`, debrief.thankYouBody] : [])].join('\n\n');
    this.keeping = true;
    this.projects.addProjectNote(this.data.projectId, note).subscribe({
      next: () => {
        this.keeping = false;
        this.notifications.success('ai.debrief.kept');
        this.changes.markForCheck();
      },
      error: () => {
        this.keeping = false;
        this.notifications.error('ai.debrief.keepError');
        this.changes.markForCheck();
      }
    });
  }

  /** Closes, saying whether a step was added or the stage changed, for the opportunity to be read again. */
  close(): void {
    this.dialogRef.close(this.roundAdded || this.moved);
  }
}
