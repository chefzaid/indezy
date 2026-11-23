import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { OfferWordSuggestion } from '../../../models';
import { OfferService } from '../../../services/offer/offer.service';

/** Suggestions declined on this device, by word, so they are not offered again here. */
const DECLINED_KEY = 'indezy.offers.declinedWordSuggestions';

/**
 * Proposes to leave out of the feed the offers mentioning a sector word that the workspace's hidden offers keep
 * mentioning (see docs/features.md#freelance-offer-feed): five in 30 days or more, a quarter of them, none kept nor
 * applied to. **Leave them out** adds the word to the feed's words to leave out; **Not now** leaves it, and it is not
 * offered again on this device. A word the search already leaves out is not offered.
 */
@Component({
  selector: 'app-offer-word-suggestions',
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  template: `
    @for (suggestion of shown; track suggestion.word) {
      <p class="suggestion" role="status" [attr.data-word-suggestion]="suggestion.word">
        <mat-icon aria-hidden="true">filter_alt_off</mat-icon>
        <span>{{ 'offers.wordSuggestion.text' | translate:{ count: suggestion.hidden, word: suggestion.word,
          sector: ('offers.wordSuggestion.sectors.' + suggestion.sector | translate) } }}</span>
        <button mat-button type="button" (click)="accept(suggestion)" data-word-suggestion-accept>
          {{ 'offers.wordSuggestion.accept' | translate }}
        </button>
        <button mat-button type="button" (click)="decline(suggestion)" data-word-suggestion-decline>
          {{ 'offers.wordSuggestion.decline' | translate }}
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
export class OfferWordSuggestionsComponent implements OnInit {
  /** The words the feed's search leaves out already. */
  @Input() exclude?: string;
  /** A word to add to the words the search leaves out. */
  @Output() readonly leftOut = new EventEmitter<string>();

  private readonly offers = inject(OfferService);
  private readonly changes = inject(ChangeDetectorRef);

  suggestions: OfferWordSuggestion[] = [];

  ngOnInit(): void {
    const declined = new Set(readDeclined());
    this.offers.wordSuggestions().subscribe({
      next: suggestions => {
        this.suggestions = (Array.isArray(suggestions) ? suggestions : []).filter(suggestion => !declined.has(suggestion.word));
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** The suggestions whose word the search does not leave out yet. */
  get shown(): OfferWordSuggestion[] {
    const excluded = fold(this.exclude ?? '');
    return this.suggestions.filter(suggestion => !excluded.split(/\s+/).some(word => word.length >= 2 && suggestion.word.includes(word)));
  }

  accept(suggestion: OfferWordSuggestion): void {
    this.suggestions = this.suggestions.filter(other => other !== suggestion);
    this.leftOut.emit(suggestion.word);
  }

  decline(suggestion: OfferWordSuggestion): void {
    this.suggestions = this.suggestions.filter(other => other !== suggestion);
    try {
      localStorage.setItem(DECLINED_KEY, JSON.stringify([...readDeclined(), suggestion.word].slice(-50)));
    } catch {
      // Storage refused (private browsing): the suggestion may come back later.
    }
  }
}

function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
}

function readDeclined(): string[] {
  try {
    const stored = JSON.parse(localStorage.getItem(DECLINED_KEY) ?? '[]');
    return Array.isArray(stored) ? stored.filter((word): word is string => typeof word === 'string') : [];
  } catch {
    return [];
  }
}
