import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, debounceTime, distinctUntilChanged, filter, switchMap, takeUntil } from 'rxjs';

import { HANDLE_PATTERN, NetworkFields, NetworkMembership } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MemberCardComponent } from '../../network/member-card/member-card.component';

/** The card's fields, in the order the tab lists them. */
export const CARD_FIELDS: (keyof NetworkFields)[] = ['photo', 'headline', 'stack', 'location', 'availability', 'languages', 'remote'];

/**
 * Profile → Network: joining the member network with a handle no other member holds, choosing what the member card
 * shows, and leaving, which hides the card at once.
 */
@Component({
  selector: 'app-profile-network',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSlideToggleModule, TranslateModule,
    MemberCardComponent],
  templateUrl: './profile-network.component.html',
  styleUrls: ['./profile-network.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileNetworkComponent implements OnInit, OnDestroy {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly fieldNames = CARD_FIELDS;
  readonly form = this.fb.group({
    handle: ['', [Validators.required, Validators.pattern(HANDLE_PATTERN)]],
    fields: this.fb.group({ photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false })
  });
  membership: NetworkMembership | null = null;
  isSaving = false;

  ngOnInit(): void {
    this.network.membership().pipe(takeUntil(this.destroy$)).subscribe({
      next: membership => this.show(membership),
      error: () => this.notifications.error('profile.network.loadError')
    });
    // A handle another member holds is said so while typing.
    this.form.controls.handle.valueChanges.pipe(
      debounceTime(300), distinctUntilChanged(),
      filter(handle => HANDLE_PATTERN.test(handle) && handle !== this.membership?.handle),
      switchMap(handle => this.network.handle(handle)),
      takeUntil(this.destroy$)
    ).subscribe(answer => {
      if (!answer.available && answer.handle === this.form.controls.handle.value) {
        this.form.controls.handle.setErrors({ taken: true });
        this.form.controls.handle.markAsTouched();
        this.changes.markForCheck();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Joins, or saves the handle and the fields once joined. */
  save(): void {
    if (this.form.invalid || this.isSaving) {
      this.form.markAllAsTouched();
      return;
    }
    const joined = this.membership?.joined === true;
    const { handle, fields } = this.form.getRawValue();
    this.isSaving = true;
    this.network.join(handle, fields).pipe(takeUntil(this.destroy$)).subscribe({
      next: membership => {
        this.isSaving = false;
        this.show(membership);
        this.notifications.success(joined ? 'profile.network.saved' : 'profile.network.joined');
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving = false;
        if (error.status === 409) {
          this.form.controls.handle.setErrors({ taken: true });
          return;
        }
        this.notifications.error('profile.network.saveError');
      }
    });
  }

  leave(): void {
    this.confirm.confirm({ messageKey: 'profile.network.leaveConfirm', confirmKey: 'profile.network.leave', danger: true })
      .subscribe(confirmed => {
        if (!confirmed) {
          return;
        }
        this.network.leave().subscribe({
          next: () => {
            this.membership = { ...this.membership!, joined: false, card: null };
            this.notifications.success('profile.network.left');
          },
          error: () => this.notifications.error('profile.network.saveError')
        });
      });
  }

  private show(membership: NetworkMembership): void {
    this.membership = membership;
    this.form.setValue({ handle: membership.handle, fields: membership.fields }, { emitEvent: false });
    this.changes.markForCheck();
  }
}
