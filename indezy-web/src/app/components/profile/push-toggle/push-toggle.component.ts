import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';

import { NotificationService } from '../../../services/notification/notification.service';
import { WebPushService } from '../../../services/push/web-push.service';

/** Why web push cannot be turned on here: the browser, the server, or the browser's permission. */
export type PushUnavailable = 'browser' | 'server' | 'denied';

/** Profile → Notifications: the network's notifications and messages pushed to this browser, once it allows them. */
@Component({
  selector: 'app-push-toggle',
  imports: [MatIconModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './push-toggle.component.html',
  styleUrls: ['./push-toggle.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PushToggleComponent implements OnInit {
  private readonly push = inject(WebPushService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  publicKey: string | null = null;
  unavailable: PushUnavailable | null = null;
  on = false;
  saving = false;

  ngOnInit(): void {
    if (!this.push.supported) {
      this.unavailable = 'browser';
      return;
    }
    if (typeof Notification !== 'undefined' && Notification.permission === 'denied') {
      this.unavailable = 'denied';
    }
    this.push.subscribed$.subscribe(subscribed => {
      this.on = subscribed;
      this.changes.markForCheck();
    });
    this.push.key().subscribe({
      next: key => {
        this.publicKey = key;
        if (!key) {
          this.unavailable = 'server';
        }
        this.changes.markForCheck();
      },
      error: () => {
        this.unavailable = 'server';
        this.changes.markForCheck();
      }
    });
  }

  async toggle(event: MatSlideToggleChange): Promise<void> {
    if (!this.publicKey) {
      event.source.checked = false;
      return;
    }
    this.saving = true;
    try {
      if (event.checked) {
        await this.push.enable(this.publicKey);
        this.notifications.success('profile.push.enabled');
      } else {
        await this.push.disable();
        this.notifications.success('profile.push.disabled');
      }
      this.on = event.checked;
    } catch {
      event.source.checked = !event.checked;
      if (typeof Notification !== 'undefined' && Notification.permission === 'denied') {
        this.unavailable = 'denied';
      }
      this.notifications.error('profile.push.error');
    } finally {
      this.saving = false;
      this.changes.markForCheck();
    }
  }
}
