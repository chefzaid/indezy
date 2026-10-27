import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { AccountEvent, AccountEventType, AccountSession } from '../../../models';
import { AccountSecurityService } from '../../../services/account-security/account-security.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

const EVENT_ICONS: Record<AccountEventType, string> = {
  SIGNED_IN: 'login', SIGN_IN_REFUSED: 'gpp_bad', SIGNED_OUT: 'logout', SESSION_REVOKED: 'phonelink_erase',
  OTHER_SESSIONS_REVOKED: 'phonelink_erase', PASSWORD_CHANGED: 'password', TWO_FACTOR_ENABLED: 'verified_user',
  TWO_FACTOR_DISABLED: 'gpp_maybe', DATA_EXPORTED: 'download', DATA_IMPORTED: 'upload', ACCOUNT_DISABLED: 'block',
  ACCOUNT_ENABLED: 'check_circle'
};

/** Where the account is signed in, each session ended on request, and what happened to the account. */
@Component({
  selector: 'app-profile-sessions',
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, TranslateModule],
  templateUrl: './profile-sessions.component.html',
  styleUrls: ['./profile-sessions.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileSessionsComponent implements OnInit, OnDestroy {
  private readonly security = inject(AccountSecurityService);
  private readonly translate = inject(TranslateService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly destroy$ = new Subject<void>();

  sessions: AccountSession[] = [];
  events: AccountEvent[] = [];
  total = 0;
  private page = 0;
  loadingMore = false;

  get others(): number {
    return this.sessions.filter(session => !session.current).length;
  }

  ngOnInit(): void {
    this.loadSessions();
    this.security.activity(0).pipe(takeUntil(this.destroy$)).subscribe({
      next: page => {
        this.events = page.events;
        this.total = page.total;
      },
      error: () => this.notifications.error('profile.sessions.loadError')
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** A phone or tablet, or a computer otherwise. */
  icon(session: AccountSession): string {
    return /Android|iPhone|iPad/.test(session.device ?? '') ? 'smartphone' : 'computer';
  }

  end(session: AccountSession): void {
    this.security.end(session.id).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.sessions = this.sessions.filter(item => item.id !== session.id);
        this.notifications.success('profile.sessions.ended');
      },
      error: () => this.notifications.error('profile.sessions.endError')
    });
  }

  /** Signs out every other browser once confirmed. */
  endOthers(): void {
    this.confirm.confirm({
      titleKey: 'profile.sessions.endOthersTitle',
      messageKey: 'profile.sessions.endOthersMessage',
      confirmKey: 'profile.sessions.endOthers',
      danger: true
    }).pipe(takeUntil(this.destroy$)).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.security.endOthers().pipe(takeUntil(this.destroy$)).subscribe({
        next: ended => {
          this.sessions = this.sessions.filter(session => session.current);
          this.notifications.success('profile.sessions.endedOthers', undefined, { count: ended.count });
        },
        error: () => this.notifications.error('profile.sessions.endError')
      });
    });
  }

  more(): void {
    this.loadingMore = true;
    this.security.activity(this.page + 1).pipe(takeUntil(this.destroy$)).subscribe({
      next: page => {
        this.page = page.page;
        this.events = [...this.events, ...page.events];
        this.total = page.total;
        this.loadingMore = false;
      },
      error: () => {
        this.loadingMore = false;
        this.notifications.error('profile.sessions.loadError');
      }
    });
  }

  eventIcon(event: AccountEvent): string {
    return EVENT_ICONS[event.type] ?? 'history';
  }

  /** What happened, in the interface language. */
  describe(event: AccountEvent): string {
    const detail = event.detail ?? '';
    const params: Record<string, string> = { device: detail || this.translate.instant('profile.sessions.unknownDevice'), count: detail };
    if (event.type === 'SIGNED_IN') {
      params['method'] = this.translate.instant(`profile.sessions.methods.${detail}`);
    }
    if (event.type === 'SIGN_IN_REFUSED') {
      params['reason'] = this.translate.instant(`profile.activity.refused.${detail === 'TWO_FACTOR' ? 'TWO_FACTOR' : 'PASSWORD'}`);
    }
    return this.translate.instant(`profile.activity.types.${event.type}`, params);
  }

  private loadSessions(): void {
    this.security.sessions().pipe(takeUntil(this.destroy$)).subscribe({
      next: sessions => this.sessions = sessions,
      error: () => this.notifications.error('profile.sessions.loadError')
    });
  }
}
