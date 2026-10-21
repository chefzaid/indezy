import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { Offer, OfferApplicationRequest, OfferStateUpdate, OfferView, publisherKey } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { leavesView } from '../../../services/offer/offer-query';
import { OfferApplyDialogComponent, OfferApplyDialogData } from '../offer-apply-dialog/offer-apply-dialog.component';

/** What the actions on one offer need from the page showing it: the feed's list, or the offer's own page. */
export interface OfferActionsHost {
  /** The view the offers are shown in: an offer that leaves it is taken out. */
  readonly view: OfferView;
  /** Offers being added to the pipeline, whose buttons wait. */
  readonly importing: Set<number>;
  /** Shows the offer with these changes. */
  patch(id: number, changes: Partial<Offer>): void;
  /** Takes out the offers that match. */
  drop(matching: (offer: Offer) => boolean): void;
  /** What is shown now, put back by the function returned when the server refuses a change. */
  checkpoint(): () => void;
}

/**
 * What can be done with one offer: save or hide it (shown at once, put back on failure), mark it seen, add it to the
 * pipeline, record or take back an application, keep a note on it and hide its publisher's offers.
 */
export class OfferActions {

  constructor(private readonly host: OfferActionsHost, private readonly offers: OfferService,
              private readonly notifications: NotificationService, private readonly dialog: MatDialog,
              private readonly translate: TranslateService) {}

  toggleSaved(offer: Offer): void {
    this.change(offer, { saved: !offer.saved });
  }

  toggleHidden(offer: Offer): void {
    this.change(offer, { hidden: !offer.hidden });
  }

  markSeen(offer: Offer): void {
    if (!offer.seen) {
      this.host.patch(offer.id, { seen: true });
      this.offers.updateState(offer.id, { seen: true }).subscribe({ error: () => undefined });
    }
  }

  importOffer(offer: Offer): void {
    this.host.importing.add(offer.id);
    this.offers.importOffer(offer.id).subscribe({
      next: project => {
        this.host.importing.delete(offer.id);
        this.host.patch(offer.id, { importedProjectId: project.id, seen: true });
        this.notifications.success('offers.notifications.imported', 4000, { role: offer.title });
      },
      error: (error: HttpErrorResponse) => {
        this.host.importing.delete(offer.id);
        this.host.patch(offer.id, {});
        if (error.status === 409) {
          this.notifications.error('offers.errors.alreadyImported');
          this.offers.get(offer.id).subscribe(full => this.host.patch(offer.id, { importedProjectId: full.importedProjectId }));
        } else {
          this.notifications.error('offers.errors.import');
        }
      }
    });
  }

  /** Asks how and when the workspace applied, then records it: the mission joins the pipeline with its steps. */
  applyToOffer(offer: Offer): void {
    this.dialog.open<OfferApplyDialogComponent, OfferApplyDialogData, OfferApplicationRequest>(OfferApplyDialogComponent,
      { data: { offer }, autoFocus: 'first-tabbable' }).afterClosed().subscribe(request => {
      if (!request) {
        return;
      }
      const language = this.translate.getCurrentLang() === 'en' ? 'EN' : 'FR';
      this.offers.apply(offer.id, { ...request, language }).subscribe({
        next: state => {
          this.host.patch(offer.id, { application: state.application, importedProjectId: state.importedProjectId, seen: true });
          this.notifications.success(request.followUpInDays ? 'offers.notifications.appliedWithFollowUp' : 'offers.notifications.applied',
            6000, { role: offer.title });
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 409) {
            this.notifications.error('offers.errors.alreadyApplied');
            this.offers.get(offer.id).subscribe(full =>
              this.host.patch(offer.id, { application: full.application, importedProjectId: full.importedProjectId }));
          } else {
            this.notifications.error('offers.errors.apply');
          }
        }
      });
    });
  }

  /** Forgets the application at once (the opportunity stays), and puts it back on failure. */
  withdrawApplication(offer: Offer): void {
    const restore = this.host.checkpoint();
    this.host.patch(offer.id, { application: undefined });
    if (this.host.view === 'APPLIED') {
      this.host.drop(item => item.id === offer.id);
    }
    this.offers.withdrawApplication(offer.id).subscribe({
      next: () => this.notifications.success('offers.notifications.withdrawn', 4000),
      error: () => {
        restore();
        this.notifications.error('offers.errors.state');
      }
    });
  }

  /** Hides the publisher's offers everywhere in the feed; the saved and hidden views keep theirs. */
  blockPublisher(offer: Offer): void {
    const name = offer.company;
    if (!name) {
      return;
    }
    this.offers.blockPublisher(name).subscribe({
      next: () => {
        if (this.host.view === 'FEED') {
          const key = publisherKey(name);
          this.host.drop(item => publisherKey(item.company) === key);
        }
        this.notifications.success('offers.notifications.publisherBlocked', 6000, { publisher: name });
      },
      error: () => this.notifications.error('offers.errors.block')
    });
  }

  /** Keeps the workspace's note on the offer (a blank one removes it), shown at once and put back on failure. */
  saveNote(offer: Offer, note: string): void {
    const before = offer.note;
    this.host.patch(offer.id, { note: note.trim() || undefined });
    this.offers.updateState(offer.id, { note }).subscribe({
      next: state => this.host.patch(offer.id, { note: state.note ?? undefined }),
      error: () => {
        this.host.patch(offer.id, { note: before });
        this.notifications.error('offers.errors.note');
      }
    });
  }

  /** Applies a state change at once, takes the offer out of a view it no longer belongs to, and undoes both on failure. */
  private change(offer: Offer, update: OfferStateUpdate): void {
    const restore = this.host.checkpoint();
    this.host.patch(offer.id, update);
    if (leavesView(this.host.view, update)) {
      this.host.drop(item => item.id === offer.id);
    }
    this.offers.updateState(offer.id, update).subscribe({
      error: () => {
        restore();
        this.notifications.error('offers.errors.state');
      }
    });
  }
}
