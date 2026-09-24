import { Injectable, OnDestroy, inject } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

/** Paginator labels ("Items per page", "1 – 12 of 96"...) in the active UI language. */
@Injectable()
export class TranslatedPaginatorIntl extends MatPaginatorIntl implements OnDestroy {
  private readonly translate = inject(TranslateService);
  private readonly subscription: Subscription;

  constructor() {
    super();
    this.subscription = this.translate.onLangChange.subscribe(() => this.updateLabels());
    this.updateLabels();
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return this.translate.instant('pagination.range', { start: 0, end: 0, length });
    }
    const start = page * pageSize;
    const end = Math.min(start + pageSize, length);
    return this.translate.instant('pagination.range', { start: start + 1, end, length });
  };

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private updateLabels(): void {
    this.itemsPerPageLabel = this.translate.instant('pagination.itemsPerPage');
    this.nextPageLabel = this.translate.instant('pagination.nextPage');
    this.previousPageLabel = this.translate.instant('pagination.previousPage');
    this.firstPageLabel = this.translate.instant('pagination.firstPage');
    this.lastPageLabel = this.translate.instant('pagination.lastPage');
    this.changes.next();
  }
}
