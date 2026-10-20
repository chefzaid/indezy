import {
  ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, LOCALE_ID, Output, ViewChild, inject
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, formatDate } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Offer } from '../../../models/offer.models';
import { relativeDay, relativeTime } from '../../../shared/utils/relative-time';
import { offerPlace, offerRate } from '../../../shared/utils/offer-format';

/** Skills shown before the rest collapse into "+n". */
const VISIBLE_SKILLS = 8;
/** Scores from which an offer matches the profile (the server's "matches my profile"), or nearly. */
const GOOD_MATCH = 60;
const FAIR_MATCH = 35;

/** One mission of the offer feed and what the workspace can do with it. */
@Component({
  selector: 'app-offer-card',
  imports: [DatePipe, FormsModule, RouterModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatMenuModule, MatProgressSpinnerModule, MatTooltipModule, TranslateModule],
  templateUrl: './offer-card.component.html',
  styleUrls: ['./offer-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferCardComponent {
  @Input({ required: true }) offer!: Offer;
  /** The full description is being loaded or shown. */
  @Input() expanded = false;
  @Input() loadingDetails = false;
  @Input() importing = false;
  /** Picked for an action on several offers at once. */
  @Input() selected = false;

  @Output() readonly toggleDetails = new EventEmitter<Offer>();
  @Output() readonly selectedChange = new EventEmitter<Offer>();
  @Output() readonly toggleSaved = new EventEmitter<Offer>();
  @Output() readonly toggleHidden = new EventEmitter<Offer>();
  @Output() readonly opened = new EventEmitter<Offer>();
  @Output() readonly importOffer = new EventEmitter<Offer>();
  /** Every offer of this offer's publisher is to leave the feed. */
  @Output() readonly blockPublisher = new EventEmitter<Offer>();
  /** The workspace applied to the offer, or takes its application back. */
  @Output() readonly applyOffer = new EventEmitter<Offer>();
  @Output() readonly withdrawApplication = new EventEmitter<Offer>();
  /** The workspace's note on the offer, changed (blank to remove it). */
  @Output() readonly saveNote = new EventEmitter<{ offer: Offer; note: string }>();

  /** The note editor is open, with its text. */
  editingNote = false;
  noteDraft = '';
  @ViewChild('noteInput') private noteInput?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('moreButton', { read: ElementRef }) private moreButton?: ElementRef<HTMLElement>;

  private readonly locale = inject(LOCALE_ID);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  editNote(): void {
    this.noteDraft = this.offer.note ?? '';
    this.editingNote = true;
    this.changes.markForCheck();
    setTimeout(() => this.noteInput?.nativeElement.focus());
  }

  submitNote(): void {
    if (this.noteDraft.trim() !== (this.offer.note ?? '')) {
      this.saveNote.emit({ offer: this.offer, note: this.noteDraft });
    }
    this.cancelNote();
  }

  deleteNote(): void {
    this.noteDraft = '';
    this.submitNote();
  }

  /** Closes the editor and gives the focus back to the menu it was opened from. */
  cancelNote(): void {
    this.editingNote = false;
    this.changes.markForCheck();
    setTimeout(() => this.moreButton?.nativeElement.focus());
  }

  /** "today", "3 days ago": when the workspace applied. */
  get appliedAgo(): string {
    return this.offer.application ? relativeDay(this.offer.application.appliedOn, this.locale) : '';
  }

  /** "Applied by email on Oct 18, 2026". */
  get appliedDetails(): string {
    const application = this.offer.application;
    if (!application) {
      return '';
    }
    return this.translate.instant(`offers.card.appliedVia.${application.via}`,
      { date: formatDate(application.appliedOn, 'mediumDate', this.locale) });
  }

  get published(): string {
    return relativeTime(this.offer.publishedAt, this.locale);
  }

  /** Translation key explaining how the publication date was obtained. */
  get publishedHint(): string {
    return `offers.card.date.${this.offer.datePrecision}`;
  }

  /** "550 € – 650 €", "≤ 600 €", "≥ 500 €" per day, or empty when the offer states no rate. */
  get rate(): string {
    return offerRate(this.offer, this.locale);
  }

  /** "Courbevoie (92)", the department or region, or the country. */
  get place(): string {
    return offerPlace(this.offer);
  }

  /** Translation key of the work mode, with the remote days when known. */
  get remoteKey(): string {
    if (this.offer.workMode === 'HYBRID') {
      return this.offer.remoteDaysPerWeek ? 'offers.card.remote.hybridDays' : 'offers.card.remote.HYBRID';
    }
    return `offers.card.remote.${this.offer.workMode}`;
  }

  get durationKey(): string {
    return this.offer.durationMonths === 1 ? 'offers.card.oneMonth' : 'offers.card.months';
  }

  /** The offer's skills, those of the profile first. */
  get visibleSkills(): string[] {
    const skills = this.offer.skills;
    return [...skills.filter(skill => this.isMatched(skill)), ...skills.filter(skill => !this.isMatched(skill))].slice(0, VISIBLE_SKILLS);
  }

  /** Whether the profile names this skill of the offer. */
  isMatched(skill: string): boolean {
    const wanted = fold(skill);
    return this.offer.match?.skills.some(matched => fold(matched) === wanted) ?? false;
  }

  get matchLevel(): 'good' | 'fair' | 'low' {
    const score = this.offer.match?.score ?? 0;
    if (score >= GOOD_MATCH) {
      return 'good';
    }
    return score >= FAIR_MATCH ? 'fair' : 'low';
  }

  /** What makes up the match: the skills found, the rate, the place and the start compared with the profile's. */
  get matchDetails(): string {
    const match = this.offer.match;
    if (!match) {
      return '';
    }
    const parts = [match.skills.length ? this.translate.instant('offers.match.skills', { skills: match.skills.join(', ') }) :
      this.translate.instant('offers.match.noSkills')];
    if (match.rate) {
      parts.push(this.translate.instant(`offers.match.rate.${match.rate}`));
    }
    if (match.place) {
      parts.push(this.translate.instant(`offers.match.place.${match.place}`));
    }
    if (match.availability) {
      parts.push(this.translate.instant(`offers.match.availability.${match.availability}`));
    }
    return parts.join(' · ');
  }

  get hiddenSkills(): number {
    return Math.max(0, this.offer.skills.length - VISIBLE_SKILLS);
  }
}

/** Case and accents aside, as the server compares skills. */
function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().trim();
}
