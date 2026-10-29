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
import { OfferShareService } from '../../../services/offer/offer-share.service';
import { describeOfferChange } from '../../../services/offer/offer-history';
import { relativeDay, relativeTime } from '../../../shared/utils/relative-time';
import { isMatchedSkill, offerDurationKey, offerPlace, offerRate, offerRemoteKey, offerSkillsMatchedFirst } from '../../../shared/utils/offer-format';

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
  /** Offers to show the full description or fold it back; the offer's own page always shows it. */
  @Input() detailsToggle = true;
  /** Can be picked for an action on several offers at once, as in the feed. */
  @Input() selectable = true;

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
  private readonly share = inject(OfferShareService);

  /** Copies the address of the offer's own page. */
  copyLink(): void {
    this.share.copyLink(this.offer.id);
  }

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
    return offerRemoteKey(this.offer);
  }

  get durationKey(): string {
    return offerDurationKey(this.offer);
  }

  /** The offer's skills, those of the profile first. */
  get visibleSkills(): string[] {
    return offerSkillsMatchedFirst(this.offer, VISIBLE_SKILLS);
  }

  /** Whether the profile names this skill of the offer. */
  isMatched(skill: string): boolean {
    return isMatchedSkill(this.offer, skill);
  }

  get matchLevel(): 'good' | 'fair' | 'low' {
    const score = this.offer.match?.score ?? 0;
    if (score >= GOOD_MATCH) {
      return 'good';
    }
    return score >= FAIR_MATCH ? 'fair' : 'low';
  }

  /** How many intermediaries publish the mission and the spread of their best rates: "3 intermediaries, 520 € – 600 €". */
  get spreadLabel(): string {
    const spread = this.offer.publisherSpread;
    if (!spread) {
      return '';
    }
    // The server sends both ends of the spread, or neither when no intermediary states a rate.
    const rates = spread.rateMax ? offerRate({ rateMin: spread.rateMin, rateMax: spread.rateMax }, this.locale) : '';
    return this.translate.instant(rates ? 'offers.card.spreadRates' : 'offers.card.spread', { count: spread.publishers, rates });
  }

  /** The latest change its site made and how often the mission came back: "Rate raised from €500 to €550 · Published 3 times". */
  get historyLine(): string {
    const history = this.offer.history;
    const parts: string[] = [];
    if (history?.changes?.length) {
      parts.push(describeOfferChange(history.changes[0], this.translate, this.locale));
    }
    if (history?.publishedTimes) {
      parts.push(this.translate.instant('offers.history.published', { count: history.publishedTimes }));
    }
    return parts.join(' · ');
  }

  /** Why the offer looks like a fake or spam listing, one reason after the other. */
  get suspicionDetails(): string {
    return (this.offer.suspicion ?? []).map(reason => this.translate.instant(`offers.suspicion.${reason}`)).join(' · ');
  }

  /** The rate against the market, in words: "+12 % vs. Java in Île-de-France". */
  get marketLabel(): string {
    const market = this.offer.rateMarket;
    if (!market) {
      return '';
    }
    const params = { percent: Math.abs(market.percent), skill: market.skill, where: this.marketWhere };
    const key = market.percent > 0 ? 'above' : market.percent < 0 ? 'below' : 'same';
    return this.translate.instant(`offers.card.market.${key}`, params);
  }

  /** What the comparison is made with: the median and how many offers it is taken over. */
  get marketHint(): string {
    const market = this.offer.rateMarket;
    if (!market) {
      return '';
    }
    return this.translate.instant('offers.card.market.hint', {
      offers: market.offers, skill: market.skill, where: this.marketWhere,
      median: new Intl.NumberFormat(this.locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(market.median)
    });
  }

  /** Well above the market, close to it, or well below: five percent either way. */
  get marketLevel(): 'above' | 'close' | 'below' {
    const percent = this.offer.rateMarket?.percent ?? 0;
    return percent >= 5 ? 'above' : percent <= -5 ? 'below' : 'close';
  }

  private get marketWhere(): string {
    const region = this.offer.rateMarket?.regionName;
    return region ? this.translate.instant('offers.card.market.inRegion', { region }) : this.translate.instant('offers.card.market.inFrance');
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
