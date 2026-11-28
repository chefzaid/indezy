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
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { AiCvItem, AiCvRefinement, AiCvRewrite, AiCvStyle, DocumentLanguage } from '../../../models';
import { AiService, aiBlobErrorKey, aiErrorKey } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProfileCvService } from '../../../services/profile-cv/profile-cv.service';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';
import { saveBlob } from '../../utils/download-file';
import { DiffSegment, changed, wordDiff } from '../../utils/word-diff';
import { newFacts } from '../../utils/fact-check';

/** The longest positioning asked for, as the server reads it. */
export const POSITIONING_MAX = 120;
const FOCUS_MAX = 80;
const LOCKED_MAX = 10;
const LOCKED_LENGTH = 200;
const ANSWER_MAX = 500;
const DOCX_TYPE = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

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
  /** The new facts last checked, for the text they were found in. */
  checked?: { text: string; facts: string[] };
  /** The AI's impact question on the block, and the freelancer's answer to weave in. */
  question?: string;
  answer?: string;
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
  imports: [FormsModule, MatButtonModule, MatButtonToggleModule, MatCheckboxModule, MatChipsModule, MatDialogModule, MatDividerModule, MatFormFieldModule,
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
  private readonly cvFiles = inject(ProfileCvService);
  private readonly cvProfiles = inject(CvProfileService);
  /** The name to save the review under as a CV profile. */
  profileName = '';
  savingProfile = false;

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
  readonly answerMax = ANSWER_MAX;
  asking = false;
  /** Everything the profile states, that the rewording is checked against. */
  private source = '';

  /** The CV file kept in the profile, to draw details from; none kept, none offered. */
  keptFile: string | null = null;
  fromFile = false;

  constructor() {
    if (this.data.tailored) {
      this.show(this.data.tailored.rewrite);
    } else {
      this.cvFiles.info().subscribe({
        next: cv => {
          this.keptFile = cv?.fileName ?? null;
          this.changes.markForCheck();
        },
        error: () => undefined
      });
    }
  }

  start(): void {
    this.rewriting = true;
    this.errorKey = null;
    this.ai.rewriteCv(this.positioning.trim(), this.style, this.language, this.fromFile && !!this.keptFile).subscribe({
      next: rewrite => {
        this.rewriting = false;
        this.profileName = this.profileName || this.positioning.trim().slice(0, 80);
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
    this.source = [rewrite.headline.before, rewrite.summary.before, ...rewrite.skillsBefore,
      ...[...rewrite.missions, ...rewrite.positions].flatMap(item => [item.title, item.before, item.stack]), rewrite.fileText]
      .filter((text): text is string => !!text).join('. ');
  }

  /** The numbers and names the block now states that the profile does not. */
  facts(block: ReviewBlock): string[] {
    if (block.checked?.text !== block.text) {
      block.checked = { text: block.text, facts: newFacts(block.text, this.source) };
    }
    return block.checked.facts;
  }

  /** The kept blocks stating something the profile does not, to check before downloading. */
  get unchecked(): ReviewBlock[] {
    return this.blocks.filter(block => block.keep && this.facts(block).length > 0);
  }

  /** Asks the AI what the vague kept blocks leave out, one question each, for the freelancer to answer. */
  askQuestions(): void {
    const blocks = this.blocks.filter(block => block.keep && !block.locked && block.text.trim())
      .map(block => ({ key: block.key, text: block.text.trim() }));
    if (!blocks.length || this.asking) {
      return;
    }
    this.asking = true;
    this.ai.cvQuestions(blocks, this.language).subscribe({
      next: questions => {
        this.asking = false;
        for (const { key, question } of questions) {
          const block = this.blocks.find(candidate => candidate.key === key);
          if (block) {
            block.question = question;
            block.answer = '';
          }
        }
        if (!questions.length) {
          this.notifications.success('ai.cv.noQuestions');
        }
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.asking = false;
        this.notifications.error(aiErrorKey(error));
        this.changes.markForCheck();
      }
    });
  }

  /** The freelancer's answer woven into the block; the question is then done. */
  weave(block: ReviewBlock): void {
    const answer = (block.answer ?? '').trim();
    if (answer) {
      this.refine(block, 'WEAVE', answer);
    }
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
  refine(block: ReviewBlock, refinement: AiCvRefinement, answer: string | null = null): void {
    const text = block.text.trim();
    if (block.locked || !block.keep || !text || block.refining) {
      return;
    }
    block.refining = true;
    this.ai.refineCv({ text, refinement, focus: refinement === 'HIGHLIGHT' ? this.focus.trim() || null : null,
      ...(answer ? { answer } : {}), locked: this.lockedPhrases, language: this.language }).subscribe({
      next: ({ text: refined }) => {
        block.refining = false;
        block.text = refined.slice(0, block.max);
        if (answer) {
          block.question = undefined;
          block.answer = undefined;
        }
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
  download(layout: 'NAMED' | 'ANONYMOUS', format: 'PDF' | 'DOCX' = 'PDF'): void {
    this.downloading = true;
    this.ai.rewrittenCv({
      headline: this.kept('headline'),
      summary: this.kept('summary'),
      skills: this.keepSkills && this.rewrite ? this.rewrite.skills : [],
      missions: this.keptItems('mission').map(({ item, description }) => ({ number: item.number, description })),
      positions: this.keptItems('position').map(({ item, description }) => ({ number: item.number, description }))
    }, layout, this.language, format).subscribe({
      next: file => {
        this.downloading = false;
        const name = `${this.language === 'EN' ? 'skills-profile' : 'dossier-competences'}${layout === 'ANONYMOUS' ? '-anonymous' : ''}` +
          `${this.tailored ? '-tailored' : ''}${format === 'DOCX' ? '.docx' : '.pdf'}`;
        saveBlob(file, name);
        this.data.made?.(new File([file], name, { type: format === 'DOCX' ? DOCX_TYPE : 'application/pdf' }));
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

  /**
   * The review kept as a CV profile, named as asked: the headline, summary and skills order kept, every mission and
   * position shown in the profile's order, with the rewritten wording where it was kept.
   */
  saveProfile(): void {
    const name = this.profileName.trim();
    const rewrite = this.rewrite;
    if (!name || !rewrite || this.savingProfile) {
      return;
    }
    const items = (kind: 'mission' | 'position', list: AiCvItem[]): { id: number; text: string | null }[] | null => {
      const kept = new Map(this.keptItems(kind).map(({ item, description }) => [item.number, description]));
      return kept.size ? list.filter(item => item.id !== null).map(item => ({ id: item.id!, text: kept.get(item.number) ?? null })) : null;
    };
    this.savingProfile = true;
    this.cvProfiles.create({ name, language: this.language, headline: this.kept('headline'), summary: this.kept('summary'),
      skills: this.keepSkills ? rewrite.skills : null, missions: items('mission', rewrite.missions), positions: items('position', rewrite.positions)
    }).subscribe({
      next: () => {
        this.savingProfile = false;
        this.notifications.success('ai.cv.profileSaved', undefined, { name });
        this.cvProfiles.changed.next();
        this.changes.markForCheck();
      },
      error: () => {
        this.savingProfile = false;
        this.notifications.error('cvProfiles.saveError');
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
