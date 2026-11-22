import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { OfferPublisherSuggestion } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';

/** Suggestions declined on this device, by publisher name, so they are not offered again here. */
const DECLINED_KEY = 'indezy.offers.declinedBlockSuggestions';

/**
 * Proposes to hide every offer of a publisher whose offers the workspace keeps hiding (see
 * docs/features.md#freelance-offer-feed): five in 30 days or more, never kept nor applied to. **Hide its offers** blocks
 * it, as Profile → Offer feed does; **Not now** leaves it, and it is not offered again on this device.
 */
@Component({
  selector: 'app-offer-block-suggestions',
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  template: `
    @for (suggestion of suggestions; track suggestion.name) {
      <p class="suggestion" role="status" [attr.data-block-suggestion]="suggestion.name">
        <mat-icon aria-hidden="true">visibility_off</mat-icon>
        <span>{{ 'offers.blockSuggestion.text' | translate:{ count: suggestion.hidden, name: suggestion.name } }}</span>
        <button mat-button type="button" (click)="block(suggestion)" data-block-suggestion-accept>
          {{ 'offers.blockSuggestion.accept' | translate }}
        </button>
        <button mat-button type="button" (click)="decline(suggestion)" data-block-suggestion-decline>
          {{ 'offers.blockSuggestion.decline' | translate }}
        </button>
      </p>
    }
  `,
  styles: [`
    .suggestion { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; margin: 0 0 12px; padding: 8px 12px; border-radius: 12px;
      background: var(--app-chip-info-bg); color: var(--app-chip-info-text); }
    .suggestion span { flex: 1 1 240px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferBlockSuggestionsComponent implements OnInit {
  /** A publisher's offers left the feed. */
  @Output() readonly blocked = new EventEmitter<string>();

  private readonly offers = inject(OfferService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  suggestions: OfferPublisherSuggestion[] = [];

  ngOnInit(): void {
    const declined = new Set(readDeclined());
    this.offers.blockSuggestions().subscribe({
      next: suggestions => {
        this.suggestions = (Array.isArray(suggestions) ? suggestions : []).filter(suggestion => !declined.has(suggestion.name));
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  block(suggestion: OfferPublisherSuggestion): void {
    this.offers.blockPublisher(suggestion.name).subscribe({
      next: () => {
        this.suggestions = this.suggestions.filter(other => other !== suggestion);
        this.notifications.success('offers.blockSuggestion.done', 4000, { name: suggestion.name });
        this.blocked.emit(suggestion.name);
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('offers.blockSuggestion.error')
    });
  }

  decline(suggestion: OfferPublisherSuggestion): void {
    this.suggestions = this.suggestions.filter(other => other !== suggestion);
    try {
      localStorage.setItem(DECLINED_KEY, JSON.stringify([...readDeclined(), suggestion.name].slice(-50)));
    } catch {
      // Storage refused (private browsing): the suggestion may come back later.
    }
  }
}

function readDeclined(): string[] {
  try {
    const stored = JSON.parse(localStorage.getItem(DECLINED_KEY) ?? '[]');
    return Array.isArray(stored) ? stored.filter((name): name is string => typeof name === 'string') : [];
  } catch {
    return [];
  }
}
