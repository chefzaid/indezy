import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { NetworkMeetup, NewMeetup } from '../../../models';
import { MeetupScope, NetworkMeetupsService } from '../../../services/network/network-meetups.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { NetworkSideComponent } from '../side/network-side.component';
import { EventCardComponent } from './event-card.component';
import { EventFormComponent } from './event-form.component';

/**
 * The network's events (`/network/events`, `?tag=` for those naming a tag): the coming ones the soonest first, those the
 * reader answered, or those they organize; found by words; **Organize an event** opens the form.
 */
@Component({
  selector: 'app-network-events',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule,
    PageHeaderComponent, NetworkMeCardComponent, NetworkNavComponent, NetworkSideComponent, EventCardComponent, EventFormComponent],
  templateUrl: './network-events.component.html',
  styleUrls: ['./network-events.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkEventsComponent implements OnInit {
  private readonly meetups = inject(NetworkMeetupsService);
  private readonly notifications = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly scopes: MeetupScope[] = ['UPCOMING', 'ATTENDING', 'ORGANIZED'];
  readonly filter = new FormControl('', { nonNullable: true });
  scope: MeetupScope = 'UPCOMING';
  tag: string | null = null;
  list: NetworkMeetup[] | null = null;
  page = 0;
  more = false;
  loadingMore = false;
  creating = false;
  saving = false;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.tag = params.get('tag');
      this.load();
    });
    this.filter.valueChanges.pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  load(): void {
    this.meetups.list(this.scope, this.tag, this.filter.value.trim()).subscribe({
      next: found => {
        this.list = found.meetups;
        this.page = 0;
        this.more = found.more;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.events.loadError')
    });
  }

  loadMore(): void {
    if (this.loadingMore) {
      return;
    }
    this.loadingMore = true;
    this.meetups.list(this.scope, this.tag, this.filter.value.trim(), this.page + 1).subscribe({
      next: found => {
        this.loadingMore = false;
        this.list = [...(this.list ?? []), ...found.meetups.filter(meetup => !(this.list ?? []).some(known => known.id === meetup.id))];
        this.page = found.page;
        this.more = found.more;
        this.changes.markForCheck();
      },
      error: () => {
        this.loadingMore = false;
        this.notifications.error('network.events.loadError');
        this.changes.markForCheck();
      }
    });
  }

  show(scope: MeetupScope): void {
    if (scope !== this.scope) {
      this.scope = scope;
      this.load();
    }
  }

  /** Shows the events of every tag again. */
  clearTag(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tag: null }, queryParamsHandling: 'merge' });
  }

  /** Organizes the event, then opens its page. */
  create(meetup: NewMeetup): void {
    this.saving = true;
    this.meetups.create(meetup).subscribe({
      next: created => {
        this.saving = false;
        this.creating = false;
        this.notifications.success('network.events.created');
        void this.router.navigate(['/network/events', created.id]);
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.notifications.error(error.status === 429 ? 'network.events.tooMany' : error.status === 400 ? 'network.events.invalid' :
          'network.events.saveError');
        this.changes.markForCheck();
      }
    });
  }

  /** An answer given on a card: those answered follow it. */
  changed(meetup: NetworkMeetup): void {
    this.list = (this.list ?? []).map(item => item.id === meetup.id ? meetup : item);
  }
}
