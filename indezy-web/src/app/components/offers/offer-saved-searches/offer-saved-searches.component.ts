import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { catchError, filter, of, switchMap } from 'rxjs';
import { OfferQuery, SavedOfferSearch, SavedSearchEmailsUpdate } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferAlertsService } from '../../../services/offer/offer-alerts.service';
import { countActiveFilters, offerQueryFromSaved } from '../../../services/offer/offer-query';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { SaveSearchDialogComponent, SaveSearchDialogData } from '../save-search-dialog/save-search-dialog.component';
import { OfferSearchSuggestionsComponent } from '../offer-search-suggestions/offer-search-suggestions.component';
import {
  SavedSearchEmailsDialogComponent,
  SavedSearchEmailsDialogData
} from '../saved-search-emails-dialog/saved-search-emails-dialog.component';

/**
 * The workspace's saved searches above the feed, each with the offers found for it since it was last opened, the
 * button that saves the current filters, and the searches suggested from the profile: shown at once while nothing is
 * saved, or when the page was opened to see them.
 */
@Component({
  selector: 'app-offer-saved-searches',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule, OfferSearchSuggestionsComponent],
  templateUrl: './offer-saved-searches.component.html',
  styleUrls: ['./offer-saved-searches.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferSavedSearchesComponent implements OnInit {
  /** The feed's current query: what saving keeps. */
  @Input({ required: true }) query!: OfferQuery;
  /** A saved search was opened: the feed shows its query. */
  @Output() searchOpened = new EventEmitter<OfferQuery>();

  searches: SavedOfferSearch[] = [];
  showSuggestions = false;

  private readonly offerService = inject(OfferService);
  private readonly alerts = inject(OfferAlertsService);
  private readonly dialog = inject(MatDialog);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.offerService.savedSearches()
      .pipe(catchError(() => of([] as SavedOfferSearch[])), takeUntilDestroyed(this.destroyRef))
      .subscribe(searches => {
        this.searches = searches;
        this.showSuggestions = this.showSuggestions || history.state?.openSuggestions === true || !searches.length && !suggestionsClosed();
        this.changes.markForCheck();
      });
  }

  toggleSuggestions(): void {
    this.showSuggestions = !this.showSuggestions;
    rememberSuggestionsClosed(!this.showSuggestions);
  }

  /** A suggestion was saved: it joins the saved searches. */
  addSaved(saved: SavedOfferSearch): void {
    this.searches = [...this.searches, saved].sort((a, b) => a.name.localeCompare(b.name));
    this.changes.markForCheck();
  }

  get canSave(): boolean {
    return countActiveFilters(this.query) > 0;
  }

  save(): void {
    const name = this.query.q?.trim() || this.query.skills[0] || this.translate.instant('offers.savedSearches.defaultName');
    const data: SaveSearchDialogData = { name };
    this.dialog.open<SaveSearchDialogComponent, SaveSearchDialogData, string>(SaveSearchDialogComponent, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter((name): name is string => !!name),
        switchMap(name => this.offerService.saveSearch(name, this.query)),
        takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: saved => {
          this.addSaved(saved);
          this.notifications.success('offers.savedSearches.saved', 4000, { name: saved.name });
        },
        error: (error: HttpErrorResponse) =>
          this.notifications.error(error.status === 409 ? 'offers.savedSearches.errors.conflict' : 'offers.savedSearches.errors.save')
      });
  }

  /** Shows the search's offers and marks it opened, so its new offers are counted afresh. */
  open(search: SavedOfferSearch): void {
    this.searches = this.searches.map(item => item.id === search.id ? { ...item, newOffers: 0 } : item);
    this.offerService.openSearch(search.id).subscribe({ next: () => this.alerts.refresh(), error: () => undefined });
    this.searchOpened.emit(offerQueryFromSaved(search.filters));
  }

  /** Asks how often the search should email its new offers, and keeps the answer. */
  editEmails(search: SavedOfferSearch): void {
    const data: SavedSearchEmailsDialogData = { search };
    this.dialog.open<SavedSearchEmailsDialogComponent, SavedSearchEmailsDialogData, SavedSearchEmailsUpdate>(SavedSearchEmailsDialogComponent,
      { data, width: '480px' })
      .afterClosed()
      .pipe(
        filter((emails): emails is SavedSearchEmailsUpdate => !!emails),
        switchMap(emails => this.offerService.updateSearchEmails(search.id, emails)),
        takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.searches = this.searches.map(item => item.id === updated.id ? { ...updated, newOffers: item.newOffers } : item);
          this.notifications.success(`offers.savedSearches.emails.saved.${updated.emails.frequency}`, 4000, { name: updated.name });
          this.changes.markForCheck();
        },
        error: () => this.notifications.error('offers.savedSearches.emails.error')
      });
  }

  /** The icon of a search's emails: on, stopped after 60 unopened days, or off. */
  emailIcon(search: SavedOfferSearch): string {
    if (search.emails.frequency !== 'OFF') {
      return 'notifications_active';
    }
    return search.emails.stoppedAt ? 'notifications_paused' : 'notifications_none';
  }

  remove(search: SavedOfferSearch): void {
    this.confirmDialog.confirm({
      titleKey: 'offers.savedSearches.deleteTitle',
      messageKey: 'offers.savedSearches.deleteMessage',
      messageParams: { name: search.name },
      confirmKey: 'common.delete',
      danger: true
    }).pipe(
      filter(Boolean),
      switchMap(() => this.offerService.deleteSearch(search.id)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.searches = this.searches.filter(item => item.id !== search.id);
        this.alerts.refresh();
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('offers.savedSearches.errors.delete')
    });
  }
}

/** Where the browser remembers that the suggestions were closed, so they no longer open by themselves. */
const SUGGESTIONS_CLOSED_KEY = 'indezy-offer-suggestions-closed';

function suggestionsClosed(): boolean {
  try {
    return localStorage.getItem(SUGGESTIONS_CLOSED_KEY) === 'true';
  } catch {
    return false;
  }
}

function rememberSuggestionsClosed(closed: boolean): void {
  try {
    if (closed) {
      localStorage.setItem(SUGGESTIONS_CLOSED_KEY, 'true');
    } else {
      localStorage.removeItem(SUGGESTIONS_CLOSED_KEY);
    }
  } catch {
    // Without storage, the suggestions open by themselves again next time.
  }
}
