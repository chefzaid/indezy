import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { AdminOverview } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { NotificationService } from '../../services/notification/notification.service';
import { AdminAccountsComponent } from './admin-accounts.component';
import { AdminFeaturesComponent } from './admin-features.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/** The admin console: how Indezy is used, its accounts, and the features turned on or off for everyone. */
@Component({
  selector: 'app-admin',
  imports: [PageHeaderComponent, CommonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, MatTabsModule, TranslateModule,
    AdminAccountsComponent, AdminFeaturesComponent],
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

  ngOnInit(): void {
    this.admin.overview().pipe(takeUntil(this.destroy$)).subscribe({
      next: overview => {
        this.overview = overview;
        this.features = Object.entries(overview.featureWorkspaces).map(([name, count]) => ({ name, count }))
          .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
      },
      error: () => this.notifications.error('admin.loadError')
    });
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
