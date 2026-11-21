import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, tap } from 'rxjs';

import { MessagingSettings, NetworkMembership } from '../../../models';
import { UserNotificationSettings, UserPreferences } from '../../../models/user-management.models';
import { CapabilitiesService } from '../../../services/admin/capabilities.service';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';

/** A tab of the profile where a setting that is not a plain switch is changed. */
export type ProfileTab = 'preferences' | 'notifications' | 'offers' | 'publicPage' | 'network' | 'account';

/** A switch of the privacy tab: which setting, and how it is saved. */
type Switch = 'contactSuggestions' | 'esnSuggestions' | 'privateCard' | 'privateViewing' | 'requests' | 'readReceipts' | 'typing' | 'presence' |
  'weeklyReports' | 'messageEmails' | 'bookingEmails' | 'shareRates';

/**
 * Profile → **Privacy**: every opt-in and opt-out in one place. The plain switches change here (the network's suggestions,
 * private card and browsing, messaging signals, the emails); the others lead to the tab where they are set.
 */
@Component({
  selector: 'app-profile-privacy',
  imports: [NgTemplateOutlet, MatButtonModule, MatIconModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './profile-privacy.component.html',
  styleUrls: ['./profile-privacy.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfilePrivacyComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly messages = inject(NetworkMessagesService);
  private readonly users = inject(UserManagementService);
  private readonly capabilities = inject(CapabilitiesService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  /** Opens another tab of the profile, where a setting is changed. */
  @Output() readonly open = new EventEmitter<ProfileTab>();

  readonly networkOn = this.capabilities.snapshot().features.SOCIAL_NETWORK === true;
  readonly publicProfiles = this.capabilities.snapshot().features.PUBLIC_PROFILES === true;
  readonly offerFeed = this.capabilities.snapshot().features.OFFER_FEED === true;
  membership: NetworkMembership | null = null;
  messaging: MessagingSettings | null = null;
  privateViewing: boolean | null = null;
  emails: UserNotificationSettings | null = null;
  preferences: UserPreferences | null = null;
  readonly saving = new Set<Switch>();

  ngOnInit(): void {
    this.users.getNotificationSettings().subscribe({ next: settings => this.show(() => this.emails = settings), error: () => undefined });
    if (!this.networkOn) {
      return;
    }
    this.users.getUserPreferences().subscribe({ next: preferences => this.show(() => this.preferences = preferences), error: () => undefined });
    this.network.membership(true).subscribe({
      next: membership => {
        this.show(() => this.membership = membership);
        if (membership.joined) {
          this.messages.messaging().subscribe({ next: settings => this.show(() => this.messaging = settings), error: () => undefined });
          this.network.pageViews(true).subscribe({ next: views => this.show(() => this.privateViewing = views.privateViewing), error: () => undefined });
        }
      },
      error: () => undefined
    });
  }

  /** Whether a switch is on, as last saved. */
  on(key: Switch): boolean {
    switch (key) {
      case 'contactSuggestions':
        return this.membership?.contactSuggestions !== false;
      case 'esnSuggestions':
      case 'privateCard':
        return this.membership?.[key] === true;
      case 'privateViewing':
        return this.privateViewing === true;
      case 'requests':
        return this.messaging?.requests !== false;
      case 'readReceipts':
      case 'typing':
      case 'presence':
        return this.messaging?.[key] === true;
      case 'shareRates':
        return this.preferences?.shareRates === true;
      default:
        return this.emails?.[key] === true;
    }
  }

  /** Saves a switch; failing, it goes back as it was. */
  set(key: Switch, event: MatSlideToggleChange): void {
    const on = event.checked;
    this.saving.add(key);
    this.save(key, on).subscribe({
      next: () => {
        this.saving.delete(key);
        this.notifications.success('profile.privacy.saved');
        this.changes.markForCheck();
      },
      error: () => {
        this.saving.delete(key);
        event.source.checked = !on;
        this.notifications.error('profile.privacy.saveError');
        this.changes.markForCheck();
      }
    });
  }

  private save(key: Switch, on: boolean): Observable<unknown> {
    switch (key) {
      case 'contactSuggestions':
        return this.network.setContactSuggestions(on).pipe(tap(membership => this.membership = membership));
      case 'esnSuggestions':
        return this.network.setEsnSuggestions(on).pipe(tap(membership => this.membership = membership));
      case 'privateCard':
        return this.network.setPrivate(on).pipe(tap(membership => this.membership = membership));
      case 'privateViewing':
        return this.network.setPrivateViewing(on).pipe(tap(views => this.privateViewing = views.privateViewing));
      case 'requests':
      case 'readReceipts':
      case 'typing':
      case 'presence':
        return this.messages.saveMessaging({ [key]: on }).pipe(tap(settings => this.messaging = settings));
      case 'shareRates':
        return this.users.updateUserPreferences({ shareRates: on }).pipe(tap(preferences => this.preferences = preferences));
      default:
        return this.users.updateNotificationSettings({ [key]: on }).pipe(tap(settings => this.emails = settings));
    }
  }

  private show(apply: () => void): void {
    apply();
    this.changes.markForCheck();
  }
}
