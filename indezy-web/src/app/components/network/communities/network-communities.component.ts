import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { Community } from '../../../models';
import { NetworkCommunitiesService } from '../../../services/network/network-communities.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { NetworkSideComponent } from '../side/network-side.component';

/**
 * The network's communities (`/network/communities`): those the reader is in first, then the largest, found by name;
 * each joined in a click; **Create a community** names one, its creator moderating it.
 */
@Component({
  selector: 'app-network-communities',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSlideToggleModule, TranslateModule,
    PageHeaderComponent, NetworkMeCardComponent, NetworkNavComponent, NetworkSideComponent],
  templateUrl: './network-communities.component.html',
  styleUrls: ['./network-communities.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkCommunitiesComponent implements OnInit {
  private readonly communities = inject(NetworkCommunitiesService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly filter = new FormControl('', { nonNullable: true });
  readonly name = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] });
  readonly description = new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) });
  readonly privateCommunity = new FormControl(false, { nonNullable: true });
  list: Community[] | null = null;
  creating = false;
  saving = false;
  readonly pending = new Set<string>();

  ngOnInit(): void {
    this.load('');
    this.filter.valueChanges.pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(typed => this.load(typed.trim()));
  }

  private load(typed: string): void {
    this.communities.list(typed).subscribe({
      next: list => {
        this.list = list;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.communities.loadError')
    });
  }

  /** Creates the community named, then opens it. */
  create(): void {
    if (this.name.invalid || this.description.invalid || this.saving) {
      return;
    }
    this.saving = true;
    this.communities.create(this.name.value.trim(), this.description.value.trim() || null, this.privateCommunity.value).subscribe({
      next: community => {
        this.saving = false;
        void this.router.navigate(['/network/communities', community.slug]);
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.notifications.error(error.status === 409 ? 'network.communities.tooMany' : 'network.communities.saveError');
        this.changes.markForCheck();
      }
    });
  }

  /** Joins a community from the list. */
  join(community: Community): void {
    this.pending.add(community.slug);
    this.communities.join(community.slug).subscribe({
      next: page => {
        this.pending.delete(community.slug);
        this.list = (this.list ?? []).map(item => item.slug === community.slug ? page.community : item);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.pending.delete(community.slug);
        this.notifications.error(error.status === 409 ? 'network.communities.removedFrom' : 'network.communities.saveError');
        this.changes.markForCheck();
      }
    });
  }
}
