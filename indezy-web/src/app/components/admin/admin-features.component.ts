import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, switchMap, takeUntil } from 'rxjs';

import { FeatureState } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { CapabilitiesService } from '../../services/admin/capabilities.service';
import { NotificationService } from '../../services/notification/notification.service';

/** The features an administrator turns on or off for every account, without a redeploy. */
@Component({
  selector: 'app-admin-features',
  imports: [MatCardModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './admin-features.component.html',
  styleUrls: ['./admin-features.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AdminFeaturesComponent implements OnInit, OnDestroy {
  private readonly admin = inject(AdminService);
  private readonly capabilities = inject(CapabilitiesService);
  private readonly notifications = inject(NotificationService);
  private readonly destroy$ = new Subject<void>();

  features: FeatureState[] = [];

  ngOnInit(): void {
    this.admin.features().pipe(takeUntil(this.destroy$)).subscribe({
      next: features => this.features = features,
      error: () => this.notifications.error('admin.loadError')
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Saves the new state, then reads this account's capabilities again so its menu follows. */
  changed(feature: FeatureState, change: MatSlideToggleChange): void {
    this.admin.setFeature(feature.flag, change.checked).pipe(
      switchMap(features => {
        this.features = features;
        return this.capabilities.refresh();
      }),
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => this.notifications.success(change.checked ? 'admin.featureOn' : 'admin.featureOff'),
      error: () => {
        change.source.checked = feature.enabled;
        this.notifications.error('admin.saveError');
      }
    });
  }
}
