import { ChangeDetectionStrategy, Component, EventEmitter, Input, LOCALE_ID, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { Offer } from '../../../models/offer.models';
import { relativeTime } from '../../../shared/utils/relative-time';

/** Skills shown before the rest collapse into "+n". */
const VISIBLE_SKILLS = 8;

/** One mission of the offer feed and what the workspace can do with it. */
@Component({
  selector: 'app-offer-card',
  imports: [DatePipe, RouterModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule,
    TranslateModule],
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

  private readonly locale = inject(LOCALE_ID);

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

  get visibleSkills(): string[] {
    return this.offer.skills.slice(0, VISIBLE_SKILLS);
  }

  get hiddenSkills(): number {
    return Math.max(0, this.offer.skills.length - VISIBLE_SKILLS);
  }
}
