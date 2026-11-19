import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';

import { MemberCard } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';

/** How long the search waits for the typing to pause. */
export const NETWORK_SEARCH_DELAY_MS = 250;
/** How many members and tags it suggests at most, each. */
const SHOWN = 5;

/** What a tag may hold. */
const TAG_WORD = /^[\p{L}\p{N}_-]{1,40}$/u;

/** What the search suggests: a member, opening their page, or a tag, opening its page. */
export type NetworkSearchOption = { kind: 'member'; member: MemberCard } | { kind: 'tag'; tag: string } | { kind: 'all'; query: string };

/**
 * The network's search, at the start of its tabs: as one types, the members every word names (name, handle, skill, city
 * or headline) and the tags in use, each opening its page, then **See all results**; Enter, or that option, opens the
 * results page (`/network/search?q=`) with the members, the posts and the tags found.
 */
@Component({
  selector: 'app-network-search',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatIconModule, TranslateModule, MemberAvatarComponent],
  templateUrl: './network-search.component.html',
  styleUrls: ['./network-search.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkSearchComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly query = new FormControl('', { nonNullable: true });
  options: NetworkSearchOption[] = [];

  ngOnInit(): void {
    this.query.valueChanges.pipe(debounceTime(NETWORK_SEARCH_DELAY_MS), distinctUntilChanged(), switchMap(value => {
      const typed = typeof value === 'string' ? value.trim() : '';
      const word = typed.replace(/^[@#]/, '');
      if (word.length < 2) {
        return of([] as NetworkSearchOption[]);
      }
      const members = typed.startsWith('#') ? of([] as MemberCard[]) :
        this.network.searchMembers(word).pipe(map(found => found.members), catchError(() => of([] as MemberCard[])));
      // A tag is one word: what a tag may hold, so that the search is no pattern.
      const tags = typed.startsWith('@') || !TAG_WORD.test(word) ? of([] as string[]) :
        this.network.suggestTags(word).pipe(catchError(() => of([] as string[])));
      return forkJoin([members, tags]).pipe(map(([found, named]) => [
        ...found.slice(0, SHOWN).map(member => ({ kind: 'member', member }) as NetworkSearchOption),
        ...named.slice(0, SHOWN).map(tag => ({ kind: 'tag', tag }) as NetworkSearchOption),
        { kind: 'all', query: typed } as NetworkSearchOption]));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(options => {
      this.options = options;
      this.changes.markForCheck();
    });
  }

  /** Keeps the field showing what was typed once an option is picked. */
  readonly display = (): string => this.query.value;

  pick(event: MatAutocompleteSelectedEvent): void {
    const option = event.option.value as NetworkSearchOption;
    this.clear();
    if (option.kind === 'all') {
      this.results(option.query);
    } else {
      void this.router.navigate(option.kind === 'member' ? ['/m', option.member.handle] : ['/network/tags', option.tag]);
    }
  }

  /** Enter on what was typed: the results page (the browser's own submit, reloading the page, held back). */
  search(event?: Event): void {
    event?.preventDefault();
    const typed = this.query.value.trim();
    if (typed.replace(/^[@#]/, '').length < 2) {
      return;
    }
    this.clear();
    this.results(typed);
  }

  private results(typed: string): void {
    void this.router.navigate(['/network/search'], { queryParams: { q: typed } });
  }

  private clear(): void {
    this.query.setValue('', { emitEvent: false });
    this.options = [];
  }
}
