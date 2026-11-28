import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { AiCvItem, AiCvRefinement, AiCvRewrite, AiCvStyle, DocumentLanguage } from '../../../models';
import { AiService, aiBlobErrorKey, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { saveBlob } from '../../utils/download-file';
import { DiffSegment, changed, wordDiff } from '../../utils/word-diff';

/** The longest positioning asked for, as the server reads it. */
export const POSITIONING_MAX = 120;
const FOCUS_MAX = 80;
const LOCKED_MAX = 10;
const LOCKED_LENGTH = 200;

/** What opens the review: nothing for the whole-CV rewrite, or a dossier tailored to an opportunity, an offer or a posting. */
export interface CvReviewData {
  /** The tailoring the AI made, in the language it was asked in. */
  tailored?: { rewrite: AiCvRewrite; language: DocumentLanguage };
  /** Each dossier downloaded from the review, as the file saved. */
  made?: (file: File) => void;
}

/** A block being reviewed: kept or not, its wording as edited, locked against refinements or not. */
export interface ReviewBlock {
  key: string;
  label: string;
  params?: Record<string, string>;
  before: string | null;
  text: string;
  keep: boolean;
  locked: boolean;
  refining: boolean;
  refinable: boolean;
  multiline: boolean;
  max: number;
  item?: AiCvItem;
  /** The diff last shown, for the text it was computed from. */
  shown?: { text: string; segments: DiffSegment[] };
}

/**
 * Reviews the CV the AI rewrote (see docs/features.md#ai-features), for a positioning and a style or tailored to an
 * opportunity, an offer or a posting: each block before and after with the words changed marked, kept or not, edited,
 * locked, or refined in one click (shorter, more technical, plainer, translated, bringing something out) with phrases
 * the AI must keep; then the dossier de compétences downloaded as reviewed and, for the whole-CV rewrite, the headline,
 * summary and past positions kept written back to the profile.
 */
@Component({
  selector: 'app-ai-cv-rewrite-dialog',
  imports: [FormsModule, MatButtonModule, MatButtonToggleModule, MatCheckboxModule, MatChipsModule, MatDialogModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatMenuModule, MatProgressSpinnerModule, MatSelectModule, MatTooltipModule, TranslateModule],
  templateUrl: './ai-cv-rewrite-dialog.component.html',
  styleUrls: ['./ai-cv-rewrite-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AiCvRewriteDialogComponent {
  private readonly ai = inject(AiService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly dialogRef = inject<MatDialogRef<AiCvRewriteDialogComponent, boolean>>(MatDialogRef);
  private readonly data = inject<CvReviewData | null>(MAT_DIALOG_DATA, { optional: true }) ?? {};

  readonly max = POSITIONING_MAX;
  readonly focusMax = FOCUS_MAX;
  readonly phraseKeys = [ENTER, COMMA] as const;
  readonly styles: AiCvStyle[] = ['IMPACT', 'CONCISE', 'TECHNICAL', 'EXECUTIVE'];
  readonly refinements: AiCvRefinement[] = ['SHORTER', 'TECHNICAL', 'PLAIN', 'TRANSLATE', 'HIGHLIGHT'];
  /** Tailored to an opportunity, an offer or a posting: reviewed and downloaded, never written to the profile. */
  readonly tailored = !!this.data.tailored;
  positioning = '';
  style: AiCvStyle = 'IMPACT';
  language: DocumentLanguage = this.data.tailored?.language ?? 'FR';
  rewrite: AiCvRewrite | null = null;
  rewriting = false;
  downloading = false;
  approving = false;
  errorKey: string | null = null;
  blocks: ReviewBlock[] = [];
  keepSkills = true;
  /** What the HIGHLIGHT refinement brings out. */
  focus = '';
  /** Phrases every refinement keeps exactly as written. */
  lockedPhrases: string[] = [];

  constructor() {
    if (this.data.tailored) {
      this.show(this.data.tailored.rewrite);
    }
  }

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

  /** Each block the AI changed, kept, its wording ready to edit; the ones it left as they were are not shown. */
  private show(rewrite: AiCvRewrite): void {
    this.rewrite = rewrite;
    const block = (key: string, label: string, before: string | null, after: string | null, options: Partial<ReviewBlock>): ReviewBlock => ({
      key, label, before, text: after ?? '', keep: !!after && changed(before, after), locked: false, refining: false, refinable: true,
      multiline: true, max: 600, ...options });
    const items = (list: AiCvItem[], kind: 'mission' | 'position'): ReviewBlock[] => list
      .filter(item => !!item.after && changed(item.before, item.after))
      .map(item => block(`${kind}-${item.number}`, `ai.cv.${kind}`, item.before, item.after, { params: { title: item.title ?? '' }, item }));
    this.blocks = [
      ...(changed(rewrite.headline.before, rewrite.headline.after) && rewrite.headline.after ?
        [block('headline', 'ai.cv.headline', rewrite.headline.before, rewrite.headline.after, { refinable: false, multiline: false, max: 120 })] : []),
      ...(changed(rewrite.summary.before, rewrite.summary.after) && rewrite.summary.after ?
        [block('summary', 'ai.cv.summary', rewrite.summary.before, rewrite.summary.after, { max: 900 })] : []),
      ...items(rewrite.missions, 'mission'),
      ...items(rewrite.positions, 'position')
    ];
    this.keepSkills = rewrite.skills.join() !== rewrite.skillsBefore.join();
  }

  /** The words changed between the block as it was and as it reads now. */
  segments(block: ReviewBlock): DiffSegment[] {
    if (block.shown?.text !== block.text) {
      block.shown = { text: block.text, segments: wordDiff(block.before, block.text) };
    }
    return block.shown.segments;
  }

  addPhrase(event: MatChipInputEvent): void {
    const phrase = event.value.trim().slice(0, LOCKED_LENGTH);
    if (phrase && !this.lockedPhrases.includes(phrase) && this.lockedPhrases.length < LOCKED_MAX) {
      this.lockedPhrases = [...this.lockedPhrases, phrase];
    }
    event.chipInput.clear();
  }

  removePhrase(phrase: string): void {
    this.lockedPhrases = this.lockedPhrases.filter(kept => kept !== phrase);
  }

  /** One block refined as asked, the locked phrases kept; a block locked is never sent. */
  refine(block: ReviewBlock, refinement: AiCvRefinement): void {
    const text = block.text.trim();
    if (block.locked || !block.keep || !text || block.refining) {
      return;
    }
    block.refining = true;
    this.ai.refineCv({ text, refinement, focus: refinement === 'HIGHLIGHT' ? this.focus.trim() || null : null, locked: this.lockedPhrases,
      language: this.language }).subscribe({
      next: ({ text: refined }) => {
        block.refining = false;
        block.text = refined.slice(0, block.max);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        block.refining = false;
        this.notifications.error(aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }

  private kept(key: string): string | null {
    const block = this.blocks.find(candidate => candidate.key === key);
    return block?.keep ? block.text.trim() || null : null;
  }

  private keptItems(kind: 'mission' | 'position'): { block: ReviewBlock; item: AiCvItem; description: string }[] {
    return this.blocks.filter(block => block.item && block.key.startsWith(kind + '-') && block.keep && block.text.trim())
      .map(block => ({ block, item: block.item!, description: block.text.trim() }));
  }

  /** The dossier as reviewed, the blocks left out as the profile says. */
  download(layout: 'NAMED' | 'ANONYMOUS'): void {
    this.downloading = true;
    this.ai.rewrittenCv({
      headline: this.kept('headline'),
      summary: this.kept('summary'),
      skills: this.keepSkills && this.rewrite ? this.rewrite.skills : [],
      missions: this.keptItems('mission').map(({ item, description }) => ({ number: item.number, description })),
      positions: this.keptItems('position').map(({ item, description }) => ({ number: item.number, description }))
    }, layout, this.language).subscribe({
      next: file => {
        this.downloading = false;
        const name = `${this.language === 'EN' ? 'skills-profile' : 'dossier-competences'}${layout === 'ANONYMOUS' ? '-anonymous' : ''}` +
          `${this.tailored ? '-tailored' : ''}.pdf`;
        saveBlob(file, name);
        this.data.made?.(new File([file], name, { type: 'application/pdf' }));
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
    this.approving = true;
    this.ai.approveCv({
      headline: this.kept('headline'),
      summary: this.kept('summary'),
      positions: this.keptItems('position').filter(({ item }) => item.id !== null).map(({ item, description }) => ({ id: item.id!, description }))
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

  /** Back to the positioning and style, the review dropped. */
  again(): void {
    this.rewrite = null;
    this.blocks = [];
  }
}
