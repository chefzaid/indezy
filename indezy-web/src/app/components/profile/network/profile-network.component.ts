import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, debounceTime, distinctUntilChanged, filter, switchMap, takeUntil } from 'rxjs';

import { HANDLE_PATTERN, MemberCard, MessagingSettings, NetworkAvailability, NetworkFields, NetworkMembership, NetworkNotificationType,
  NetworkRestrictions } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ProfileFollowRequestsComponent } from './follow-requests/profile-follow-requests.component';
import { MemberCardComponent } from '../../network/member-card/member-card.component';

/** Why the registry did not verify the member, in the interface language. */
export function verificationErrorKey(error: HttpErrorResponse): string {
  const message: unknown = error.error?.message;
  if (error.status === 409 && typeof message === 'string') {
    if (message.startsWith('Add your SIRET')) {
      return 'profile.network.verifyErrors.noSiret';
    }
    if (message.startsWith('The registry has no active establishment')) {
      return 'profile.network.verifyErrors.notActive';
    }
    if (message.startsWith('The registry names someone else')) {
      return 'profile.network.verifyErrors.someoneElse';
    }
  }
  return error.status === 503 ? 'profile.network.verifyErrors.unavailable' : 'profile.network.saveError';
}

/** The card's fields, in the order the tab lists them. */
export const CARD_FIELDS: (keyof NetworkFields)[] = ['photo', 'headline', 'stack', 'location', 'availability', 'languages', 'remote'];

/**
 * Profile → Network: joining the member network with a handle no other member holds, choosing what the member card
 * shows, and leaving, which hides the card at once.
 */
/** Where a kind of notification goes. */
export type NoticeChannel = 'OFF' | 'APP' | 'EMAIL';

@Component({
  selector: 'app-profile-network',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatSlideToggleModule, MatButtonToggleModule, TranslateModule,
    MemberCardComponent, ProfileFollowRequestsComponent],
  templateUrl: './profile-network.component.html',
  styleUrls: ['./profile-network.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileNetworkComponent implements OnInit, OnDestroy {
  private readonly network = inject(NetworkService);
  private readonly messagesApi = inject(NetworkMessagesService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly fieldNames = CARD_FIELDS;
  readonly restrictionKinds = ['muted', 'blocked'] as const;
  readonly form = this.fb.group({
    handle: ['', [Validators.required, Validators.pattern(HANDLE_PATTERN)]],
    fields: this.fb.group({ photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false }),
    availability: this.fb.group({ mode: 'AUTO' as NetworkAvailability, date: '' })
  });
  readonly modes: NetworkAvailability[] = ['AUTO', 'AVAILABLE', 'ON_MISSION', 'LISTENING'];
  /** The About section shown on the member's page, and whether their won missions are listed there. */
  readonly aboutForm = this.fb.group({ about: ['', Validators.maxLength(1000)], showMissions: false });
  private aboutLoaded = false;
  isSavingAbout = false;

  /** Saves the About section and whether the won missions show. */
  saveAbout(): void {
    if (this.aboutForm.invalid || this.isSavingAbout) {
      return;
    }
    this.isSavingAbout = true;
    this.network.saveAbout(this.aboutForm.getRawValue()).subscribe({
      next: about => {
        this.isSavingAbout = false;
        this.aboutForm.setValue({ about: about.about ?? '', showMissions: about.showMissions });
        this.aboutForm.markAsPristine();
        this.notifications.success('profile.network.aboutSaved');
        this.changes.markForCheck();
      },
      error: () => {
        this.isSavingAbout = false;
        this.notifications.error('profile.network.saveError');
        this.changes.markForCheck();
      }
    });
  }

  /** Available from a day, or on a mission until one: the modes that name a day. */
  get dated(): boolean {
    const mode = this.form.controls.availability.controls.mode.value;
    return mode === 'AVAILABLE' || mode === 'ON_MISSION';
  }
  membership: NetworkMembership | null = null;
  /** The members muted and blocked, to undo either; read once joined. */
  restrictions: NetworkRestrictions | null = null;
  isSaving = false;
  isVerifying = false;
  readonly noticeTypes: NetworkNotificationType[] = ['FOLLOW', 'FOLLOW_REQUEST', 'FOLLOW_ACCEPTED', 'LIKE', 'COMMENT_LIKE',
    'COMMENT', 'REPLY', 'MENTION', 'REPOST', 'COMMUNITY_POST', 'COMMUNITY_REQUEST', 'COMMUNITY_ACCEPTED', 'COMMUNITY_MODERATOR', 'MISSION_MATCH',
    'IDEA_STATUS', 'MEETUP_REMINDER', 'MEETUP_CANCELLED'];
  /** The kinds of notification turned off; read once joined. */
  noticesOff: NetworkNotificationType[] | null = null;
  /** The kinds also emailed. */
  noticesEmailed: NetworkNotificationType[] = [];

  /** How a kind reaches the member: off, in the app, or by email too. */
  channel(type: NetworkNotificationType): NoticeChannel {
    return this.noticesOff!.includes(type) ? 'OFF' : this.noticesEmailed.includes(type) ? 'EMAIL' : 'APP';
  }

  /** Sends a kind of notification nowhere, to the app, or by email too. */
  setChannel(type: NetworkNotificationType, channel: NoticeChannel): void {
    const off = this.noticesOff!.filter(item => item !== type);
    const emailed = this.noticesEmailed.filter(item => item !== type);
    this.network.saveNotificationSettings(channel === 'OFF' ? [...off, type] : off, channel === 'EMAIL' ? [...emailed, type] : emailed)
      .subscribe({
        next: settings => {
          this.noticesOff = settings.off;
          this.noticesEmailed = settings.emailed ?? [];
        },
        error: () => this.notifications.error('profile.network.saveError')
      });
  }

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
    const { handle, fields, availability } = this.form.getRawValue();
    this.isSaving = true;
    this.network.join(handle, fields, { mode: availability.mode, date: this.dated && availability.date ? availability.date : null }).pipe(takeUntil(this.destroy$)).subscribe({
      next: membership => {
        this.isSaving = false;
        this.show(membership);
        this.notifications.success(joined ? 'profile.network.saved' : 'profile.network.joined');
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving = false;
        if (error.status === 409 && String(error.error?.message ?? '').startsWith('The moderators ended')) {
          this.notifications.error('profile.network.banned');
          return;
        }
        if (error.status === 409) {
          this.form.controls.handle.setErrors({ taken: true });
          return;
        }
        this.notifications.error('profile.network.saveError');
      }
    });
  }

  /** Asks the public registry whether the billing SIRET is the member's own business; says why not when it is not. */
  verify(): void {
    this.isVerifying = true;
    this.network.verify().pipe(takeUntil(this.destroy$)).subscribe({
      next: membership => {
        this.isVerifying = false;
        this.show(membership);
        this.notifications.success('profile.network.verifiedDone');
      },
      error: (error: HttpErrorResponse) => {
        this.isVerifying = false;
        this.notifications.error(verificationErrorKey(error));
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

  /** Stops muting or blocking a member listed here. */
  lift(kind: 'muted' | 'blocked', member: MemberCard): void {
    const request = kind === 'muted' ? this.network.mute(member.handle, false) : this.network.block(member.handle, false);
    request.subscribe({
      next: () => {
        this.restrictions = { ...this.restrictions!, [kind]: this.restrictions![kind].filter(item => item.handle !== member.handle) };
        this.notifications.success(kind === 'muted' ? 'network.page.unmutedDone' : 'network.page.unblockedDone');
      },
      error: () => this.notifications.error('profile.network.saveError')
    });
  }

  /** Whether the member shows and sees read receipts and who is typing; read once joined. */
  messaging: MessagingSettings | null = null;

  /** Shows or hides read receipts, or who is typing, both ways. */
  setMessaging(key: keyof MessagingSettings, on: boolean): void {
    this.messagesApi.saveMessaging({ [key]: on }).subscribe({
      next: settings => {
        this.messaging = settings;
        this.changes.markForCheck();
      },
      error: () => {
        this.messaging = this.messaging ? { ...this.messaging } : null;
        this.notifications.error('profile.network.saveError');
        this.changes.markForCheck();
      }
    });
  }

  private show(membership: NetworkMembership): void {
    if (membership.joined && !this.aboutLoaded) {
      this.aboutLoaded = true;
      this.network.myAbout().pipe(takeUntil(this.destroy$)).subscribe({
        next: about => {
          this.aboutForm.setValue({ about: about.about ?? '', showMissions: about.showMissions });
          this.aboutForm.markAsPristine();
          this.changes.markForCheck();
        },
        error: () => this.aboutLoaded = false
      });
    }
    if (membership.joined && !this.messaging) {
      this.messagesApi.messaging().pipe(takeUntil(this.destroy$)).subscribe({
        next: settings => {
          this.messaging = settings;
          this.changes.markForCheck();
        },
        error: () => this.messaging = null
      });
    }
    if (membership.joined && !this.noticesOff) {
      this.network.notificationSettings().pipe(takeUntil(this.destroy$)).subscribe({
        next: settings => {
          this.noticesOff = settings.off;
          this.noticesEmailed = settings.emailed ?? [];
        },
        error: () => this.noticesOff = null
      });
    }
    if (membership.joined && !this.restrictions) {
      this.network.restrictions().pipe(takeUntil(this.destroy$)).subscribe({
        next: restrictions => this.restrictions = restrictions,
        error: () => this.restrictions = null
      });
    }
    this.membership = membership;
    this.form.setValue({ handle: membership.handle, fields: membership.fields,
      availability: { mode: membership.availability.mode, date: membership.availability.date ?? '' } }, { emitEvent: false });
    this.changes.markForCheck();
  }
}
