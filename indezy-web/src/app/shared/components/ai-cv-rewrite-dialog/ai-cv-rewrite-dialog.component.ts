import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { AiCvItem, AiCvRewrite, AiCvStyle, DocumentLanguage } from '../../../models';
import { AiService, aiBlobErrorKey, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { saveBlob } from '../../utils/download-file';

/** The longest positioning asked for, as the server reads it. */
export const POSITIONING_MAX = 120;

/** A block being reviewed: kept or not, and its wording as edited. */
interface Draft {
  keep: boolean;
  text: string;
}

/** A mission or position being reviewed. */
interface ItemDraft extends Draft {
  item: AiCvItem;
}

/**
 * Rewrites the whole CV with AI for a positioning and a style, tied to no opportunity (see docs/features.md#ai-features):
 * each block before and after, kept or not and editable, then the dossier de compétences downloaded as reviewed, and the
 * headline, summary and past positions kept written back to the profile.
 */
@Component({
  selector: 'app-ai-cv-rewrite-dialog',
  imports: [FormsModule, MatButtonModule, MatButtonToggleModule, MatCheckboxModule, MatDialogModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatMenuModule, MatProgressSpinnerModule, MatSelectModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'ai.cv.title' | translate }}</h2>
    <mat-dialog-content>
      @if (rewriting) {
        <div class="busy"><mat-spinner diameter="28" [attr.aria-label]="'ai.cv.busy' | translate"></mat-spinner></div>
      } @else if (rewrite) {
        <section data-ai-cv-review>
          <p class="intro">{{ 'ai.cv.review' | translate }}</p>
          <div class="block" data-ai-cv-block="headline">
            <mat-checkbox [(ngModel)]="headline.keep" data-ai-cv-keep="headline">{{ 'ai.cv.headline' | translate }}</mat-checkbox>
            <p class="before">{{ rewrite.headline.before || ('ai.cv.empty' | translate) }}</p>
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'ai.cv.rewritten' | translate }}</mat-label>
              <input matInput [(ngModel)]="headline.text" [disabled]="!headline.keep" maxlength="120" data-ai-cv-text="headline">
            </mat-form-field>
          </div>
          <div class="block" data-ai-cv-block="summary">
            <mat-checkbox [(ngModel)]="summary.keep" data-ai-cv-keep="summary">{{ 'ai.cv.summary' | translate }}</mat-checkbox>
            <p class="before">{{ rewrite.summary.before || ('ai.cv.empty' | translate) }}</p>
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'ai.cv.rewritten' | translate }}</mat-label>
              <textarea matInput rows="4" [(ngModel)]="summary.text" [disabled]="!summary.keep" maxlength="900" data-ai-cv-text="summary"></textarea>
            </mat-form-field>
          </div>
          <div class="block" data-ai-cv-block="skills">
            <mat-checkbox [(ngModel)]="keepSkills" data-ai-cv-keep="skills">{{ 'ai.cv.skills' | translate }}</mat-checkbox>
            <p class="before">{{ rewrite.skillsBefore.join(', ') }}</p>
            <p class="after" data-ai-cv-skills>{{ rewrite.skills.join(', ') }}</p>
          </div>
          @for (draft of missions; track draft.item.number) {
            <div class="block" [attr.data-ai-cv-block]="'mission-' + draft.item.number">
              <mat-checkbox [(ngModel)]="draft.keep">{{ 'ai.cv.mission' | translate:{ title: draft.item.title } }}</mat-checkbox>
              <p class="before">{{ draft.item.before }}</p>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'ai.cv.rewritten' | translate }}</mat-label>
                <textarea matInput rows="3" [(ngModel)]="draft.text" [disabled]="!draft.keep" maxlength="600"></textarea>
              </mat-form-field>
            </div>
          }
          @for (draft of positions; track draft.item.number) {
            <div class="block" [attr.data-ai-cv-block]="'position-' + draft.item.number">
              <mat-checkbox [(ngModel)]="draft.keep">{{ 'ai.cv.position' | translate:{ title: draft.item.title } }}</mat-checkbox>
              <p class="before">{{ draft.item.before }}</p>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'ai.cv.rewritten' | translate }}</mat-label>
                <textarea matInput rows="3" [(ngModel)]="draft.text" [disabled]="!draft.keep" maxlength="600"></textarea>
              </mat-form-field>
            </div>
          }
          <p class="disclaimer">{{ 'ai.disclaimer' | translate }}</p>
        </section>
      } @else {
        <p class="intro">{{ 'ai.cv.intro' | translate }}</p>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>{{ 'ai.cv.positioning' | translate }}</mat-label>
          <input matInput [(ngModel)]="positioning" [maxlength]="max" [placeholder]="'ai.cv.positioningHint' | translate" data-ai-cv-positioning>
        </mat-form-field>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>{{ 'ai.cv.style' | translate }}</mat-label>
          <mat-select [(ngModel)]="style" data-ai-cv-style>
            @for (option of styles; track option) {
              <mat-option [value]="option">{{ 'ai.cv.styles.' + option | translate }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-button-toggle-group [(ngModel)]="language" [attr.aria-label]="'ai.cv.language' | translate" data-ai-cv-language>
          <mat-button-toggle value="FR">Français</mat-button-toggle>
          <mat-button-toggle value="EN">English</mat-button-toggle>
        </mat-button-toggle-group>
        @if (errorKey) {
          <p class="error" role="alert">{{ errorKey | translate }}</p>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (rewrite) {
        <button mat-button type="button" (click)="rewrite = null" data-ai-cv-again>{{ 'ai.cv.again' | translate }}</button>
        <button mat-stroked-button type="button" (click)="approve()" [disabled]="approving" data-ai-cv-approve>
          <mat-icon>person</mat-icon>{{ 'ai.cv.approve' | translate }}
        </button>
        <button mat-flat-button color="primary" type="button" [matMenuTriggerFor]="pdfMenu" [disabled]="downloading" data-ai-cv-download>
          <mat-icon>download</mat-icon>{{ 'ai.cv.download' | translate }}
        </button>
        <mat-menu #pdfMenu="matMenu">
          <button mat-menu-item type="button" (click)="download('NAMED')" data-ai-cv-pdf="NAMED"><mat-icon>person</mat-icon>{{ 'ai.cv.named' | translate }}</button>
          <button mat-menu-item type="button" (click)="download('ANONYMOUS')" data-ai-cv-pdf="ANONYMOUS"><mat-icon>visibility_off</mat-icon>{{ 'ai.cv.anonymous' | translate }}</button>
        </mat-menu>
      } @else {
        <button mat-button type="button" mat-dialog-close>{{ 'common.close' | translate }}</button>
        <button mat-flat-button color="primary" type="button" (click)="start()" [disabled]="rewriting" data-ai-cv-rewrite>
          <mat-icon>auto_awesome</mat-icon>{{ 'ai.cv.rewrite' | translate }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .busy { display: flex; justify-content: center; padding: 24px; }
    .intro { margin: 0 0 12px; color: var(--mat-sys-on-surface-variant); }
    .wide { width: 100%; }
    .block { display: flex; flex-direction: column; gap: 4px; padding: 8px 0; border-top: 1px solid var(--mat-sys-outline-variant); }
    .block mat-form-field { width: 100%; }
    .before { margin: 0; color: var(--mat-sys-on-surface-variant); font-size: .875rem; white-space: pre-line; text-decoration: line-through;
      text-decoration-color: color-mix(in srgb, currentColor 40%, transparent); }
    .after { margin: 0; }
    .disclaimer { margin: 12px 0 0; color: var(--mat-sys-on-surface-variant); font-size: .8125rem; }
    .error { color: var(--mat-sys-error); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiCvRewriteDialogComponent {
  private readonly ai = inject(AiService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly dialogRef = inject<MatDialogRef<AiCvRewriteDialogComponent, boolean>>(MatDialogRef);

  readonly max = POSITIONING_MAX;
  readonly styles: AiCvStyle[] = ['IMPACT', 'CONCISE', 'TECHNICAL', 'EXECUTIVE'];
  positioning = '';
  style: AiCvStyle = 'IMPACT';
  language: DocumentLanguage = 'FR';
  rewrite: AiCvRewrite | null = null;
  rewriting = false;
  downloading = false;
  approving = false;
  errorKey: string | null = null;
  headline: Draft = { keep: false, text: '' };
  summary: Draft = { keep: false, text: '' };
  keepSkills = true;
  missions: ItemDraft[] = [];
  positions: ItemDraft[] = [];

  start(): void {
    this.rewriting = true;
    this.errorKey = null;
    this.ai.rewriteCv(this.positioning.trim(), this.style, this.language).subscribe({
      next: rewrite => {
        this.rewriting = false;
        this.show(rewrite);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.rewriting = false;
        this.errorKey = aiErrorKey(error);
        this.changes.markForCheck();
      }
    });
  }

  /** Each block kept when the AI changed it, its wording ready to edit. */
  private show(rewrite: AiCvRewrite): void {
    this.rewrite = rewrite;
    const draft = (before: string | null, after: string | null): Draft => ({ keep: !!after && after !== before, text: after ?? '' });
    this.headline = draft(rewrite.headline.before, rewrite.headline.after);
    this.summary = draft(rewrite.summary.before, rewrite.summary.after);
    this.keepSkills = rewrite.skills.join() !== rewrite.skillsBefore.join();
    const items = (list: AiCvItem[]): ItemDraft[] => list.filter(item => !!item.after && item.after !== item.before)
      .map(item => ({ item, ...draft(item.before, item.after) }));
    this.missions = items(rewrite.missions);
    this.positions = items(rewrite.positions);
  }

  /** The dossier as reviewed, the blocks left out as the profile says. */
  download(layout: 'NAMED' | 'ANONYMOUS'): void {
    const kept = (drafts: ItemDraft[]): { number: number; description: string }[] => drafts.filter(draft => draft.keep && draft.text.trim())
      .map(draft => ({ number: draft.item.number, description: draft.text.trim() }));
    this.downloading = true;
    this.ai.rewrittenCv({
      headline: this.headline.keep ? this.headline.text.trim() || null : null,
      summary: this.summary.keep ? this.summary.text.trim() || null : null,
      skills: this.keepSkills && this.rewrite ? this.rewrite.skills : [],
      missions: kept(this.missions),
      positions: kept(this.positions)
    }, layout, this.language).subscribe({
      next: file => {
        this.downloading = false;
        saveBlob(file, `${this.language === 'EN' ? 'skills-profile' : 'dossier-competences'}${layout === 'ANONYMOUS' ? '-anonymous' : ''}.pdf`);
        this.changes.markForCheck();
      },
      error: (error: unknown) => {
        this.downloading = false;
        void aiBlobErrorKey(error).then(key => this.notifications.error(key));
        this.changes.markForCheck();
      }
    });
  }

  /** Writes the kept headline, summary and past positions to the profile; won missions stay their opportunities'. */
  approve(): void {
    const positions = this.positions.filter(draft => draft.keep && draft.text.trim() && draft.item.id !== null)
      .map(draft => ({ id: draft.item.id!, description: draft.text.trim() }));
    this.approving = true;
    this.ai.approveCv({
      headline: this.headline.keep ? this.headline.text.trim() || null : null,
      summary: this.summary.keep ? this.summary.text.trim() || null : null,
      positions
    }).subscribe({
      next: () => {
        this.approving = false;
        this.notifications.success('ai.cv.approved');
        this.dialogRef.close(true);
      },
      error: () => {
        this.approving = false;
        this.notifications.error('ai.cv.approveError');
        this.changes.markForCheck();
      }
    });
  }
}
