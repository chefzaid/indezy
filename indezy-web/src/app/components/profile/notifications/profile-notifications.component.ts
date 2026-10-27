import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxChange, MatCheckboxModule } from '@angular/material/checkbox';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';

import { DIGEST_SECTIONS, DigestSection, UserNotificationSettings } from '../../../models/user-management.models';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferAlertsService } from '../../../services/offer/offer-alerts.service';

type Switch = 'weeklyReports' | 'newOffersBadge' | 'bookingEmails';

/** Which notifications the account gets, each saved as soon as it is switched. */
@Component({
  selector: 'app-profile-notifications',
  imports: [RouterLink, MatCardModule, MatCheckboxModule, MatDividerModule, MatIconModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './profile-notifications.component.html',
  styleUrls: ['./profile-notifications.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileNotificationsComponent implements OnInit {
  private readonly users = inject(UserManagementService);
  private readonly notifications = inject(NotificationService);
  private readonly offerAlerts = inject(OfferAlertsService);

  settings?: UserNotificationSettings;
  readonly sections = DIGEST_SECTIONS;

  ngOnInit(): void {
    this.users.getNotificationSettings().subscribe({
      next: settings => this.settings = settings,
      error: () => this.notifications.error('profile.errors.loadingNotifications')
    });
  }

  /** Whether the digest gives this section. */
  kept(section: DigestSection): boolean {
    return this.settings!.weeklyDigestSections.includes(section);
  }

  /** Keeps or leaves out one section of the digest, the last one kept staying. Put back when it cannot be saved. */
  sectionChanged(section: DigestSection, change: MatCheckboxChange): void {
    const kept = this.sections.filter(item => item === section ? change.checked : this.kept(item));
    this.save({ weeklyDigestSections: kept }, () => change.source.checked = !change.checked);
  }

  /** Saves one switch; the menu's badge follows its own at once. Put back when it cannot be saved. */
  changed(which: Switch, change: MatSlideToggleChange): void {
    this.save({ [which]: change.checked }, () => change.source.checked = !change.checked, which);
  }

  private save(update: Partial<UserNotificationSettings>, undo: () => void, which?: Switch): void {
    const before = this.settings;
    this.users.updateNotificationSettings(update).subscribe({
      next: saved => {
        this.settings = saved;
        if (which === 'newOffersBadge') {
          if (saved.newOffersBadge) {
            this.offerAlerts.start();
          } else {
            this.offerAlerts.stop();
          }
        }
        this.notifications.success('profile.notificationsUpdated');
      },
      error: () => {
        undo();
        this.settings = before;
        this.notifications.error('profile.errors.updatingNotifications');
      }
    });
  }
}
