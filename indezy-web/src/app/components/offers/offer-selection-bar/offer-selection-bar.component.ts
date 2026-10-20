import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { OfferStateUpdate, OfferView } from '../../../models/offer.models';

/** What can be done to every selected offer at once. */
export type OfferBulkAction = { update: OfferStateUpdate } | 'add';

/**
 * The bar of the offers selected on the page: how many, selecting the whole page or none, and the actions on all of
 * them, as the view allows (unsave in the saved view, show again in the hidden one).
 */
@Component({
  selector: 'app-offer-selection-bar',
  imports: [MatButtonModule, MatCheckboxModule, MatIconModule, MatTooltipModule, TranslateModule],
  templateUrl: './offer-selection-bar.component.html',
  styleUrl: './offer-selection-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferSelectionBarComponent {
  @Input({ required: true }) count = 0;
  /** Offers on the page. */
  @Input({ required: true }) pageCount = 0;
  @Input({ required: true }) view: OfferView = 'FEED';
  /** An action is under way. */
  @Input() busy = false;

  @Output() readonly selectPage = new EventEmitter<void>();
  @Output() readonly clear = new EventEmitter<void>();
  @Output() readonly act = new EventEmitter<OfferBulkAction>();

  get allSelected(): boolean {
    return this.count >= this.pageCount;
  }

  toggleAll(): void {
    if (this.allSelected) {
      this.clear.emit();
    } else {
      this.selectPage.emit();
    }
  }

  save(): void {
    this.act.emit({ update: { saved: this.view !== 'SAVED' } });
  }

  hide(): void {
    this.act.emit({ update: { hidden: this.view !== 'HIDDEN' } });
  }

  markSeen(): void {
    this.act.emit({ update: { seen: true } });
  }

  add(): void {
    this.act.emit('add');
  }
}
