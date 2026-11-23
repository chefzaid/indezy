import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { AdminOverview, HelpFeedbackTotals } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { NotificationService } from '../../services/notification/notification.service';
import { AdminAccountsComponent } from './admin-accounts.component';
import { AdminFeaturesComponent } from './admin-features.component';
import { AdminSupportComponent } from './admin-support.component';
import { AdminModerationComponent } from './admin-moderation.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/** The admin console: how Indezy is used, its accounts, the features turned on or off for everyone, and the network's moderation. */
@Component({
  selector: 'app-admin',
  imports: [AdminSupportComponent, PageHeaderComponent, MatCardModule, MatProgressSpinnerModule, MatTabsModule, TranslateModule,
    AdminAccountsComponent, AdminFeaturesComponent, AdminModerationComponent],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AdminComponent implements OnInit, OnDestroy {
  private readonly admin = inject(AdminService);
  private readonly notifications = inject(NotificationService);
  private readonly destroy$ = new Subject<void>();

  overview?: AdminOverview;
  features: { name: string; count: number }[] = [];
  /** How each help article helped its readers lately; empty until read. */
  helpFeedback: HelpFeedbackTotals[] = [];

  ngOnInit(): void {
    this.admin.overview().pipe(takeUntil(this.destroy$)).subscribe({
      next: overview => {
        this.overview = overview;
        this.features = Object.entries(overview.featureWorkspaces).map(([name, count]) => ({ name, count }))
          .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
      },
      error: () => this.notifications.error('admin.loadError')
    });
    this.admin.helpFeedback().pipe(takeUntil(this.destroy$)).subscribe({
      next: totals => this.helpFeedback = Array.isArray(totals) ? totals : [],
      error: () => undefined
    });
  }

  /** The share of an article's answers saying it helped, in whole percent. */
  helpfulShare(totals: HelpFeedbackTotals): number {
    const answers = totals.helpful + totals.unhelpful;
    return answers ? Math.round(totals.helpful * 100 / answers) : 0;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** The feature's share of the accounts, in whole percent. */
  share(count: number): number {
    return this.overview?.accounts ? Math.round(count * 100 / this.overview.accounts) : 0;
  }
}
