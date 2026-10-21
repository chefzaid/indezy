import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, EventEmitter, LOCALE_ID, OnInit, Output, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { filter, switchMap } from 'rxjs';
import { OfferProfileField, OfferQuery, OfferSearchSuggestion, SavedOfferSearch } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { offerQueryFromSaved } from '../../../services/offer/offer-query';
import { describeOfferSearch, offerSearchName } from '../../../services/offer/offer-search-describe';
import { NotificationService } from '../../../services/notification/notification.service';
import { SaveSearchDialogComponent, SaveSearchDialogData } from '../save-search-dialog/save-search-dialog.component';

/** A suggestion as the list shows it: its filters in words. */
export interface ShownSuggestion {
  suggestion: OfferSearchSuggestion;
  query: OfferQuery;
  parts: string[];
  name: string;
}

/**
 * Searches suggested from the profile and the pipeline (see docs/features.md#suggested-searches), each with what it
 * finds now, to preview in the feed, save as a saved search, or dismiss for good.
 */
@Component({
  selector: 'app-offer-search-suggestions',
  imports: [RouterModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule, TranslateModule],
  templateUrl: './offer-search-suggestions.component.html',
  styleUrls: ['./offer-search-suggestions.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferSearchSuggestionsComponent implements OnInit {
  /** A suggestion to look at: the feed shows its offers, nothing is saved. */
  @Output() readonly preview = new EventEmitter<OfferQuery>();
  /** A suggestion saved as a saved search. */
  @Output() readonly saved = new EventEmitter<SavedOfferSearch>();

  shown: ShownSuggestion[] = [];
  missing: OfferProfileField[] = [];
  loading = true;
  failed = false;

  private readonly offerService = inject(OfferService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly locale = inject(LOCALE_ID);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.failed = false;
    this.offerService.searchSuggestions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ suggestions, missing }) => {
        this.shown = suggestions.map(suggestion => this.toShown(suggestion));
        this.missing = missing;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: () => {
        this.failed = true;
        this.loading = false;
        this.changes.markForCheck();
      }
    });
  }

  /** The profile fields left empty, in words. */
  get missingFields(): string {
    return this.missing.map(field => this.translate.instant(`offers.suggestions.fields.${field}`)).join(', ');
  }

  /** "12 open offers", or "1 open offer": the key ending in One for one. */
  countKey(key: string, count: number): string {
    return count === 1 ? `${key}One` : key;
  }

  save(item: ShownSuggestion): void {
    const data: SaveSearchDialogData = { name: item.name };
    this.dialog.open<SaveSearchDialogComponent, SaveSearchDialogData, string>(SaveSearchDialogComponent, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter((name): name is string => !!name),
        switchMap(name => this.offerService.saveSearch(name, item.query)),
        takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: saved => {
          this.shown = this.shown.filter(shown => shown !== item);
          this.saved.emit(saved);
          this.notifications.success('offers.savedSearches.saved', 4000, { name: saved.name });
          this.changes.markForCheck();
        },
        error: (error: HttpErrorResponse) =>
          this.notifications.error(error.status === 409 ? 'offers.savedSearches.errors.conflict' : 'offers.savedSearches.errors.save')
      });
  }

  /** Not suggested again: gone at once, back if the server refuses. */
  dismiss(item: ShownSuggestion): void {
    const index = this.shown.indexOf(item);
    this.shown = this.shown.filter(shown => shown !== item);
    this.offerService.dismissSuggestion(item.suggestion.filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      error: () => {
        this.shown = [...this.shown.slice(0, index), item, ...this.shown.slice(index)];
        this.notifications.error('offers.suggestions.errors.dismiss');
        this.changes.markForCheck();
      }
    });
  }

  private toShown(suggestion: OfferSearchSuggestion): ShownSuggestion {
    const query = offerQueryFromSaved(suggestion.filters);
    const parts = describeOfferSearch(query, suggestion.place, this.translate, this.locale);
    return { suggestion, query, parts, name: offerSearchName(parts) };
  }
}
