import { ChangeDetectionStrategy, Component, EventEmitter, Input, LOCALE_ID, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Offer } from '../../../models/offer.models';
import { relativeTime } from '../../../shared/utils/relative-time';

/** Skills shown before the rest collapse into "+n". */
const VISIBLE_SKILLS = 8;
/** Scores from which an offer matches the profile (the server's "matches my profile"), or nearly. */
const GOOD_MATCH = 60;
const FAIR_MATCH = 35;

/** One mission of the offer feed and what the workspace can do with it. */
@Component({
  selector: 'app-offer-card',
  imports: [DatePipe, RouterModule, MatButtonModule, MatCardModule, MatIconModule, MatMenuModule, MatProgressSpinnerModule,
    MatTooltipModule, TranslateModule],
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

  @Output() readonly toggleDetails = new EventEmitter<Offer>();
  @Output() readonly toggleSaved = new EventEmitter<Offer>();
  @Output() readonly toggleHidden = new EventEmitter<Offer>();
  @Output() readonly opened = new EventEmitter<Offer>();
  @Output() readonly importOffer = new EventEmitter<Offer>();
  /** Every offer of this offer's publisher is to leave the feed. */
  @Output() readonly blockPublisher = new EventEmitter<Offer>();

  private readonly locale = inject(LOCALE_ID);
  private readonly translate = inject(TranslateService);

  get published(): string {
    return relativeTime(this.offer.publishedAt, this.locale);
  }

  /** Translation key explaining how the publication date was obtained. */
  get publishedHint(): string {
    return `offers.card.date.${this.offer.datePrecision}`;
  }

  /** "550 – 650 €", "≤ 600 €", "≥ 500 €" per day, or empty when the offer states no rate. */
  get rate(): string {
    const { rateMin, rateMax } = this.offer;
    const amount = (value: number): string => new Intl.NumberFormat(this.locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
      .format(value);
    if (rateMin !== undefined && rateMax !== undefined) {
      return rateMin === rateMax ? amount(rateMin) : `${amount(rateMin)} – ${amount(rateMax)}`;
    }
    if (rateMax !== undefined) {
      return `≤ ${amount(rateMax)}`;
    }
    return rateMin === undefined ? '' : `≥ ${amount(rateMin)}`;
  }

  /** "Courbevoie (92)", the department or region, or the country. */
  get place(): string {
    const { city, departmentCode, departmentName, regionName, countryCode } = this.offer;
    if (city) {
      return departmentCode && countryCode === 'FR' ? `${city} (${departmentCode})` : city;
    }
    return departmentName ?? regionName ?? (countryCode && countryCode !== 'FR' ? countryCode : '');
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

  /** What makes up the match: the skills found, the rate and the place compared with the profile's. */
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
