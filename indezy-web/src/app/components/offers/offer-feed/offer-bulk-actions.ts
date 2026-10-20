import { forkJoin } from 'rxjs';
import { Offer, OfferPage, OfferQuery, OfferStateUpdate } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { dropOffers, inChunks, patchOffers } from '../../../services/offer/offer-page';
import { leavesView } from '../../../services/offer/offer-query';
import { OfferBulkAction } from '../offer-selection-bar/offer-selection-bar.component';

/** Offers added to the pipeline per request, as the server allows. */
const IMPORT_SIZE = 50;

/** What the bulk actions need from the feed showing the page. */
export interface OfferPageHost {
  page: OfferPage | null;
  readonly query: OfferQuery;
  /** Offers being added to the pipeline, whose buttons wait. */
  readonly importing: Set<number>;
  /** Searches the page again. */
  retry(): void;
  /** The page changed: draw it again. */
  redraw(): void;
}

/**
 * The offers of the page picked for an action on several at once, and those actions: a state change applied at once
 * (taking out the offers that leave the view, put back on failure; hiding can be undone), and adding to the pipeline.
 */
export class OfferBulkActions {
  readonly selected = new Set<number>();
  busy = false;

  constructor(private readonly host: OfferPageHost, private readonly offers: OfferService,
              private readonly notifications: NotificationService) {}

  toggle(offer: Offer): void {
    if (!this.selected.delete(offer.id)) {
      this.selected.add(offer.id);
    }
    this.host.redraw();
  }

  selectPage(): void {
    this.host.page?.content.forEach(offer => this.selected.add(offer.id));
    this.host.redraw();
  }

  clear(): void {
    this.selected.clear();
    this.host.redraw();
  }

  act(action: OfferBulkAction): void {
    if (action === 'add') {
      this.addToPipeline();
    } else {
      this.update(action.update);
    }
  }

  private update(update: OfferStateUpdate): void {
    const page = this.host.page;
    const ids = [...this.selected];
    if (!ids.length || !page) {
      return;
    }
    const picked = new Set(ids);
    this.host.page = patchOffers(leavesView(this.host.query.view, update) ? dropOffers(page, offer => picked.has(offer.id)) : page,
      picked, update);
    this.clear();
    this.offers.updateStates(ids, update).subscribe({
      next: () => {
        if (update.hidden) {
          this.notifications.successWithAction(countKey('offers.bulk.hidden', ids.length), 'offers.bulk.undo', 8000, { count: ids.length })
            .subscribe(() => this.offers.updateStates(ids, { hidden: false }).subscribe({
              next: () => this.host.retry(),
              error: () => this.notifications.error('offers.errors.state')
            }));
        } else {
          this.notifications.success(countKey('offers.bulk.done', ids.length), 4000, { count: ids.length });
        }
      },
      error: () => {
        this.host.page = page;
        this.notifications.error('offers.errors.state');
        this.host.redraw();
      }
    });
  }

  /** Adds the selected offers not yet in the pipeline, each mission once. */
  private addToPipeline(): void {
    const ids = (this.host.page?.content ?? []).filter(offer => this.selected.has(offer.id) && !offer.importedProjectId)
      .map(offer => offer.id);
    if (!ids.length) {
      this.notifications.info('offers.bulk.allInPipeline', 4000);
      return;
    }
    ids.forEach(id => this.host.importing.add(id));
    this.busy = true;
    this.host.redraw();
    forkJoin(inChunks(ids, IMPORT_SIZE).map(chunk => this.offers.importOffers(chunk))).subscribe({
      next: results => {
        const added = results.flatMap(result => result.imported);
        const all = [...added, ...results.flatMap(result => result.alreadyInPipeline)];
        if (this.host.page) {
          this.host.page = all.reduce((page, { offerId, projectId }) => patchOffers(page, [offerId],
            { importedProjectId: projectId, seen: true }), this.host.page);
        }
        this.endImport(ids);
        this.notifications.success(added.length ? countKey('offers.bulk.imported', added.length) : 'offers.bulk.importedNone', 6000,
          { count: added.length });
      },
      error: () => {
        this.endImport(ids);
        this.notifications.error('offers.errors.import');
      }
    });
  }

  private endImport(ids: number[]): void {
    ids.forEach(id => this.host.importing.delete(id));
    this.busy = false;
    this.clear();
  }
}

/** The key of a message about `count` offers: its `One` form for a single one. */
function countKey(key: string, count: number): string {
  return count === 1 ? `${key}One` : key;
}
