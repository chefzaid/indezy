import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, Observable, catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';

import { IDEA_STATUSES, IdeaStatus, NetworkIdea } from '../../../models';
import { IdeaOrder, NetworkIdeasService } from '../../../services/network/network-ideas.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { NetworkSideComponent } from '../side/network-side.component';

/** The material icon of each status. */
export const IDEA_ICONS: Record<IdeaStatus, string> = {
  OPEN: 'lightbulb', PLANNED: 'event', IN_PROGRESS: 'construction', DONE: 'rocket_launch', DECLINED: 'block', MERGED: 'call_merge'
};

/**
 * The ideas board (`/network/ideas`, one idea first at `/network/ideas/:id`): ideas for Indezy that members propose and
 * vote for, the most voted or the newest first, narrowed by status or by words; the similar ideas show while one is
 * written, to vote for those rather than repeat them. The administrators answer each with a status and a word, and merge
 * duplicates.
 */
@Component({
  selector: 'app-network-ideas',
  imports: [DatePipe, NgTemplateOutlet, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule,
    MatSelectModule, TranslateModule, PageHeaderComponent, MemberAvatarComponent, NetworkMeCardComponent, NetworkNavComponent, NetworkSideComponent],
  templateUrl: './network-ideas.component.html',
  styleUrls: ['./network-ideas.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkIdeasComponent implements OnInit {
  private readonly ideasService = inject(NetworkIdeasService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly statuses = IDEA_STATUSES;
  readonly filter = new FormControl('', { nonNullable: true });
  readonly title = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(5), Validators.maxLength(120)] });
  readonly description = new FormControl('', { nonNullable: true, validators: Validators.maxLength(2000) });
  readonly editTitle = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(5), Validators.maxLength(120)] });
  readonly editDescription = new FormControl('', { nonNullable: true, validators: Validators.maxLength(2000) });
  readonly answerStatus = new FormControl<IdeaStatus>('PLANNED', { nonNullable: true });
  readonly answerNote = new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) });
  readonly mergeInto = new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(1)] });

  sort: IdeaOrder = 'TOP';
  status: IdeaStatus | null = null;
  ideas: NetworkIdea[] | null = null;
  /** The idea asked for by its address, shown above the board. */
  focused: NetworkIdea | null = null;
  page = 0;
  more = false;
  administrator = false;
  loadingMore = false;
  creating = false;
  saving = false;
  /** The ideas like the one being written, to vote for rather than repeat. */
  similar: NetworkIdea[] = [];
  /** The idea being changed by its author, answered or merged by an administrator. */
  editing: number | null = null;
  answering: number | null = null;
  merging: number | null = null;
  readonly pending = new Set<number>();

  ngOnInit(): void {
    this.load();
    this.filter.valueChanges.pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
    this.title.valueChanges.pipe(debounceTime(300), map(typed => typed.trim()), distinctUntilChanged(),
      switchMap(typed => this.similarTo(typed)), takeUntilDestroyed(this.destroyRef))
      .subscribe(found => {
        this.similar = found;
        this.changes.markForCheck();
      });
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = Number(params.get('id'));
      if (!id) {
        this.focused = null;
        return;
      }
      this.ideasService.get(id).subscribe({
        next: idea => {
          this.focused = idea;
          this.changes.markForCheck();
        },
        error: () => this.notifications.error('network.ideas.notFound')
      });
    });
  }

  private similarTo(typed: string): Observable<NetworkIdea[]> {
    if (typed.length < 5) {
      return of([]);
    }
    // Unread, nothing is suggested, and the next words typed are looked up again.
    return this.ideasService.list('TOP', null, typed).pipe(map(found => found.ideas.slice(0, 3)), catchError(() => of([])));
  }

  /** The board from its first page, as ordered and narrowed. */
  load(): void {
    this.ideasService.list(this.sort, this.status, this.filter.value.trim()).subscribe({
      next: found => {
        this.ideas = found.ideas;
        this.page = 0;
        this.more = found.more;
        this.administrator = found.administrator;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.ideas.loadError')
    });
  }

  /** The next 20 ideas. */
  loadMore(): void {
    if (this.loadingMore) {
      return;
    }
    this.loadingMore = true;
    this.ideasService.list(this.sort, this.status, this.filter.value.trim(), this.page + 1).subscribe({
      next: found => {
        this.loadingMore = false;
        this.ideas = [...(this.ideas ?? []), ...found.ideas.filter(idea => !(this.ideas ?? []).some(known => known.id === idea.id))];
        this.page = found.page;
        this.more = found.more;
        this.changes.markForCheck();
      },
      error: () => {
        this.loadingMore = false;
        this.notifications.error('network.ideas.loadError');
        this.changes.markForCheck();
      }
    });
  }

  order(sort: IdeaOrder): void {
    if (this.sort !== sort) {
      this.sort = sort;
      this.load();
    }
  }

  narrow(status: IdeaStatus | null): void {
    if (this.status !== status) {
      this.status = status;
      this.load();
    }
  }

  /** The icon of an idea's status. */
  iconOf(idea: NetworkIdea): string {
    return IDEA_ICONS[idea.status];
  }

  /** Whether an idea still takes votes: answered done or declined, or merged, it does not. */
  votable(idea: NetworkIdea): boolean {
    return idea.status === 'OPEN' || idea.status === 'PLANNED' || idea.status === 'IN_PROGRESS';
  }

  /** Votes for an idea, or takes the vote back, the count following at once. */
  vote(idea: NetworkIdea): void {
    if (this.pending.has(idea.id) || (!idea.voted && !this.votable(idea))) {
      return;
    }
    this.pending.add(idea.id);
    this.replace({ ...idea, voted: !idea.voted, votes: idea.votes + (idea.voted ? -1 : 1) });
    this.ideasService.vote(idea.id, !idea.voted).subscribe({
      next: saved => {
        this.pending.delete(idea.id);
        this.replace(saved);
      },
      error: () => {
        this.pending.delete(idea.id);
        this.replace(idea);
        this.notifications.error('network.ideas.saveError');
      }
    });
  }

  /** Proposes the idea written, shown first. */
  create(): void {
    if (this.title.invalid || this.description.invalid || this.saving) {
      return;
    }
    this.saving = true;
    this.ideasService.create(this.title.value.trim(), this.description.value.trim() || null).subscribe({
      next: idea => {
        this.saving = false;
        this.creating = false;
        this.title.reset();
        this.description.reset();
        this.similar = [];
        this.ideas = [idea, ...(this.ideas ?? [])];
        this.notifications.success('network.ideas.created');
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.notifications.error(error.status === 409 ? 'network.ideas.tooMany' : 'network.ideas.saveError');
        this.changes.markForCheck();
      }
    });
  }

  startEdit(idea: NetworkIdea): void {
    this.editing = idea.id;
    this.editTitle.setValue(idea.title);
    this.editDescription.setValue(idea.description ?? '');
  }

  saveEdit(idea: NetworkIdea): void {
    if (this.editTitle.invalid || this.editDescription.invalid) {
      return;
    }
    this.ideasService.update(idea.id, this.editTitle.value.trim(), this.editDescription.value.trim() || null).subscribe({
      next: saved => {
        this.editing = null;
        this.replace(saved);
      },
      error: () => this.notifications.error('network.ideas.saveError')
    });
  }

  /** Deletes an idea, once confirmed: its author's while only they voted, any for the administrators. */
  remove(idea: NetworkIdea): void {
    this.confirm.confirm({ messageKey: 'network.ideas.deleteConfirm', confirmKey: 'common.delete', danger: true })
      .pipe(switchMap(confirmed => confirmed ? this.ideasService.remove(idea.id).pipe(map(() => true)) : EMPTY))
      .subscribe({
        next: () => {
          this.ideas = (this.ideas ?? []).filter(item => item.id !== idea.id);
          if (this.focused?.id === idea.id) {
            this.focused = null;
          }
          this.notifications.success('network.ideas.deleted');
          this.changes.markForCheck();
        },
        error: (error: HttpErrorResponse) => this.notifications.error(error.status === 409 ? 'network.ideas.cannotDelete' : 'network.ideas.saveError')
      });
  }

  startAnswer(idea: NetworkIdea): void {
    this.answering = idea.id;
    this.merging = null;
    this.answerStatus.setValue(idea.status === 'MERGED' ? 'PLANNED' : idea.status);
    this.answerNote.setValue(idea.statusNote ?? '');
  }

  /** An administrator's answer: a status and a word, told to those who voted when the status changes. */
  saveAnswer(idea: NetworkIdea): void {
    if (this.answerNote.invalid) {
      return;
    }
    this.ideasService.setStatus(idea.id, this.answerStatus.value, this.answerNote.value.trim() || null).subscribe({
      next: saved => {
        this.answering = null;
        this.replace(saved);
      },
      error: () => this.notifications.error('network.ideas.saveError')
    });
  }

  startMerge(idea: NetworkIdea): void {
    this.merging = idea.id;
    this.answering = null;
    this.mergeInto.reset();
  }

  /** Merges a duplicate into the idea whose number is given, moving its votes there. */
  saveMerge(idea: NetworkIdea): void {
    const into = this.mergeInto.value;
    if (this.mergeInto.invalid || into === null || into === idea.id) {
      return;
    }
    this.ideasService.merge(idea.id, into).subscribe({
      next: kept => {
        this.merging = null;
        this.ideas = (this.ideas ?? []).filter(item => item.id !== idea.id);
        if (this.focused?.id === idea.id) {
          this.focused = null;
        }
        this.replace(kept);
        this.notifications.success('network.ideas.merged', undefined, { title: kept.title });
      },
      error: (error: HttpErrorResponse) => this.notifications.error(error.status === 404 ? 'network.ideas.notFound' : 'network.ideas.saveError')
    });
  }

  private replace(idea: NetworkIdea): void {
    this.ideas = (this.ideas ?? []).map(item => item.id === idea.id ? idea : item);
    this.similar = this.similar.map(item => item.id === idea.id ? idea : item);
    if (this.focused?.id === idea.id) {
      this.focused = idea;
    }
    this.changes.markForCheck();
  }
}
