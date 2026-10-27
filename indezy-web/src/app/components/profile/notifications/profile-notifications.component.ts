import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';

import { UserNotificationSettings } from '../../../models/user-management.models';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferAlertsService } from '../../../services/offer/offer-alerts.service';

type Switch = 'weeklyReports' | 'newOffersBadge';

/** Which notifications the account gets, each saved as soon as it is switched. */
@Component({
  selector: 'app-profile-notifications',
  imports: [RouterLink, MatCardModule, MatDividerModule, MatIconModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './profile-notifications.component.html',
  styleUrls: ['./profile-notifications.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileNotificationsComponent implements OnInit {
  private readonly users = inject(UserManagementService);
  private readonly notifications = inject(NotificationService);
  private readonly offerAlerts = inject(OfferAlertsService);

  settings?: UserNotificationSettings;

  ngOnInit(): void {
    this.users.getNotificationSettings().subscribe({
      next: settings => this.settings = settings,
      error: () => this.notifications.error('profile.errors.loadingNotifications')
    });
  }

  /** Saves one switch; the menu's badge follows its own at once. Put back when it cannot be saved. */
  changed(which: Switch, change: MatSlideToggleChange): void {
    const before = this.settings;
    this.users.updateNotificationSettings({ [which]: change.checked }).subscribe({
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
        change.source.checked = !change.checked;
        this.settings = before;
        this.notifications.error('profile.errors.updatingNotifications');
      }
    });
  }
}
