import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';

import { GlobalSearchResult, SearchHit } from '../../models/search.models';
import { GlobalSearchService } from '../../services/search/global-search.service';
import { TextPart, highlight } from '../../shared/utils/highlight';

export const MIN_QUERY = 2;
type Kind = keyof GlobalSearchResult;

/** A hit as shown: where it leads, under which heading. */
export interface SearchOption {
  kind: Kind;
  hit: SearchHit;
  link: (string | number)[];
}

const KINDS: { kind: Kind; icon: string; link: (hit: SearchHit) => (string | number)[] }[] = [
  { kind: 'projects', icon: 'work', link: hit => ['/projects', hit.id] },
  { kind: 'clients', icon: 'business', link: hit => ['/clients', hit.id] },
  { kind: 'contacts', icon: 'person', link: hit => ['/contacts', hit.id] },
  { kind: 'notes', icon: 'sticky_note_2', link: hit => ['/projects', hit.projectId ?? hit.id] }
];

/**
 * Searches the whole workspace from anywhere (Alt + Shift + S or the toolbar's search button): opportunities, clients,
 * contacts and journal notes, with the words found highlighted. The arrow keys move through the results and Enter
 * opens one.
 */
@Component({
  selector: 'app-global-search-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatIconModule, MatProgressBarModule, TranslateModule],
  templateUrl: './global-search-dialog.component.html',
  styleUrls: ['./global-search-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class GlobalSearchDialogComponent implements OnInit {
  private readonly searchService = inject(GlobalSearchService);
  private readonly router = inject(Router);
  private readonly dialogRef = inject(MatDialogRef<GlobalSearchDialogComponent>);
  private readonly destroyRef = inject(DestroyRef);

  readonly kinds = KINDS;
  readonly query = new FormControl('', { nonNullable: true });
  result: GlobalSearchResult | null = null;
  options: SearchOption[] = [];
  active = 0;
  loading = false;
  failed = false;

  ngOnInit(): void {
    this.query.valueChanges.pipe(
      map(value => value.trim()),
      debounceTime(200),
      distinctUntilChanged(),
      tap(() => this.failed = false),
      switchMap(value => {
        if (value.length < MIN_QUERY) {
          return of(null);
        }
        this.loading = true;
        return this.searchService.search(value).pipe(catchError(() => {
          this.failed = true;
          return of(null);
        }));
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => {
      this.loading = false;
      this.result = result;
      this.options = result ? KINDS.flatMap(({ kind, link }) => result[kind].map(hit => ({ kind, hit, link: link(hit) }))) : [];
      this.active = 0;
    });
  }

  get empty(): boolean {
    return !!this.result && this.options.length === 0;
  }

  optionsOf(kind: Kind): SearchOption[] {
    return this.options.filter(option => option.kind === kind);
  }

  indexOf(option: SearchOption): number {
    return this.options.indexOf(option);
  }

  parts(text: string | undefined): TextPart[] {
    return highlight(text, this.query.value);
  }

  onKeydown(event: KeyboardEvent): void {
    if (!this.options.length) {
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      this.active = (this.active + step + this.options.length) % this.options.length;
    } else if (event.key === 'Enter') {
      event.preventDefault();
      this.open(this.options[this.active]);
    }
  }

  open(option: SearchOption): void {
    this.dialogRef.close();
    void this.router.navigate(option.link);
  }
}
