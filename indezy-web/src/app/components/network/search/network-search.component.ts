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
export type NetworkSearchOption = { kind: 'member'; member: MemberCard } | { kind: 'tag'; tag: string } | { kind: 'all'; query: string } |
  { kind: 'recent'; query: string } | { kind: 'forget' };

/** Where the last searches are kept, on this device only. */
export const RECENT_SEARCHES_KEY = 'indezy-network-recent-searches';
/** How many are kept. */
export const RECENT_KEPT = 5;

/** The last searches made on this device, the latest first; none when the browser keeps nothing. */
export function recentSearches(): string[] {
  try {
    const kept = JSON.parse(localStorage.getItem(RECENT_SEARCHES_KEY) ?? '[]');
    return Array.isArray(kept) ? kept.filter((item): item is string => typeof item === 'string').slice(0, RECENT_KEPT) : [];
  } catch {
    return [];
  }
}

function remember(query: string): void {
  try {
    const kept = [query, ...recentSearches().filter(item => item.toLowerCase() !== query.toLowerCase())].slice(0, RECENT_KEPT);
    localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(kept));
  } catch {
    // Nothing kept: the browser refuses storage.
  }
}

function forgetAll(): void {
  try {
    localStorage.removeItem(RECENT_SEARCHES_KEY);
  } catch {
    // Nothing to forget.
  }
}

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
        return of(typed ? [] : this.recentOptions());
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

  /** The last searches, offered while the field is empty, with **Clear**. */
  recentOptions(): NetworkSearchOption[] {
    const recent = recentSearches();
    return recent.length ? [...recent.map(query => ({ kind: 'recent', query }) as NetworkSearchOption), { kind: 'forget' }] : [];
  }

  /** The field gets the focus empty: the last searches are offered. */
  focused(): void {
    if (!this.query.value.trim()) {
      this.options = this.recentOptions();
      this.changes.markForCheck();
    }
  }

  /** Keeps the field showing what was typed once an option is picked. */
  readonly display = (): string => this.query.value;

  pick(event: MatAutocompleteSelectedEvent): void {
    const option = event.option.value as NetworkSearchOption;
    this.clear();
    if (option.kind === 'all' || option.kind === 'recent') {
      this.results(option.query);
    } else if (option.kind === 'forget') {
      forgetAll();
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
    remember(typed);
    void this.router.navigate(['/network/search'], { queryParams: { q: typed } });
  }

  private clear(): void {
    this.query.setValue('', { emitEvent: false });
    this.options = [];
  }
}
