import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs';

import { AdminAccount, AdminAccountPage } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';

/** Accounts found by email or name, newest first, each disabled or enabled again after a confirmation. */
@Component({
  selector: 'app-admin-accounts',
  imports: [CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule,
    TranslateModule],
  templateUrl: './admin-accounts.component.html',
  styleUrls: ['./admin-accounts.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AdminAccountsComponent implements OnInit, OnDestroy {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly destroy$ = new Subject<void>();
  private readonly searches = new Subject<{ query: string; page: number }>();

  query = '';
  result?: AdminAccountPage;

  ngOnInit(): void {
    this.searches.pipe(
      debounceTime(250),
      distinctUntilChanged((a, b) => a.query === b.query && a.page === b.page),
      switchMap(search => this.admin.accounts(search.query, search.page)),
      takeUntil(this.destroy$)
    ).subscribe({
      next: result => this.result = result,
      error: () => this.notifications.error('admin.loadError')
    });
    this.search(0);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  search(page = 0): void {
    this.searches.next({ query: this.query.trim(), page });
  }

  pageChanged(event: PageEvent): void {
    this.search(event.pageIndex);
  }

  /** Disables or enables the account again once confirmed, and shows its new state. */
  toggle(account: AdminAccount): void {
    const disabling = !account.disabledAt;
    const name = `${account.firstName ?? ''} ${account.lastName ?? ''}`.trim() || account.email;
    this.confirm.confirm({
      titleKey: disabling ? 'admin.disableTitle' : 'admin.enableTitle',
      messageKey: disabling ? 'admin.disableMessage' : 'admin.enableMessage',
      messageParams: { name },
      confirmKey: disabling ? 'admin.disable' : 'admin.enable',
      danger: disabling
    }).pipe(takeUntil(this.destroy$)).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.admin.setDisabled(account.id, disabling).pipe(takeUntil(this.destroy$)).subscribe({
        next: saved => {
          if (this.result) {
            this.result = { ...this.result, accounts: this.result.accounts.map(item => item.id === saved.id ? saved : item) };
          }
          this.notifications.success(disabling ? 'admin.disabledDone' : 'admin.enabledDone');
        },
        error: () => this.notifications.error('admin.saveError')
      });
    });
  }
}
