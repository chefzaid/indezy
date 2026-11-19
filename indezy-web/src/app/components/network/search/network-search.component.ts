import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, of, switchMap } from 'rxjs';

import { MemberCard } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';

/** How long the search waits for the typing to pause. */
export const NETWORK_SEARCH_DELAY_MS = 250;
/** How many members and tags it suggests at most, each. */
const SHOWN = 5;

/** What the search suggests: a member, opening their page, or a tag, opening its page. */
export type NetworkSearchOption = { kind: 'member'; member: MemberCard } | { kind: 'tag'; tag: string };

/**
 * The network's search, at the start of its tabs: as one types, the members whose name or handle starts with it and the
 * tags in use, each opening its page; Enter on what was typed finds the members showing it as a skill.
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
      const members = typed.startsWith('#') ? of([] as MemberCard[]) : this.network.mentions(word).pipe(catchError(() => of([] as MemberCard[])));
      const tags = typed.startsWith('@') ? of([] as string[]) : this.network.suggestTags(word).pipe(catchError(() => of([] as string[])));
      return forkJoin([members, tags]).pipe(switchMap(([found, named]) => of([
        ...found.slice(0, SHOWN).map(member => ({ kind: 'member', member }) as NetworkSearchOption),
        ...named.slice(0, SHOWN).map(tag => ({ kind: 'tag', tag }) as NetworkSearchOption)])));
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
    void this.router.navigate(option.kind === 'member' ? ['/m', option.member.handle] : ['/network/tags', option.tag]);
  }

  /** Enter on what was typed: the members showing it as a skill (the browser's own submit, reloading the page, held back). */
  search(event?: Event): void {
    event?.preventDefault();
    const typed = this.query.value.trim().replace(/^[@#]/, '');
    if (!typed) {
      return;
    }
    this.clear();
    void this.router.navigate(['/network/members'], { queryParams: { skill: typed } });
  }

  private clear(): void {
    this.query.setValue('', { emitEvent: false });
    this.options = [];
  }
}
