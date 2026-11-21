import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, forkJoin } from 'rxjs';

import { EXPENSE_CATEGORIES, Expense, ExpenseCategory, ExpenseDraft, ExpenseSummary, ProjectStatus } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { ExpenseService } from '../../services/expense/expense.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { ExpenseDialogComponent, ExpenseDialogData } from './expense-dialog/expense-dialog.component';

/**
 * The workspace's business expenses of a year: the list, the totals by month and category, and what they change in the
 * net income under its legal status.
 */
@Component({
  selector: 'app-expenses',
  imports: [PageHeaderComponent, DatePipe, DecimalPipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatMenuModule,
    MatProgressSpinnerModule, TranslateModule],
  templateUrl: './expenses.component.html',
  styleUrls: ['./expenses.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ExpensesComponent implements OnInit {
  private readonly expenseService = inject(ExpenseService);
  private readonly projectService = inject(ProjectService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);

  readonly thisYear = new Date().getFullYear();
  year = this.thisYear;
  expenses: Expense[] = [];
  summary: ExpenseSummary | null = null;
  loading = true;

  ngOnInit(): void {
    this.load();
  }

  /** The categories spent on this year, the largest first. */
  get categories(): { category: ExpenseCategory; amount: number }[] {
    const byCategory = this.summary?.byCategory ?? {};
    return EXPENSE_CATEGORIES.filter(category => (byCategory[category] ?? 0) > 0)
      .map(category => ({ category, amount: byCategory[category]! }))
      .sort((a, b) => b.amount - a.amount);
  }

  /** The largest month, for the bars' scale. */
  get largestMonth(): number {
    return Math.max(0, ...(this.summary?.months ?? []));
  }

  /** The bar of a month, as a share of the largest one. */
  barWidth(amount: number): number {
    return this.largestMonth > 0 ? Math.round(amount * 100 / this.largestMonth) : 0;
  }

  /** January to December, as the date pipe writes them. */
  monthDate(index: number): Date {
    return new Date(this.year, index, 1);
  }

  changeYear(step: number): void {
    this.year += step;
    this.load();
  }

  write(expense?: Expense): void {
    const freelanceId = this.auth.getUser()?.id;
    if (!freelanceId) {
      return;
    }
    this.projectService.getByFreelanceId(freelanceId).subscribe({
      next: projects => {
        const data: ExpenseDialogData = { projects: projects.filter(project => project.status === ProjectStatus.WON || project.id === expense?.projectId),
          expense };
        this.dialog.open(ExpenseDialogComponent, { data, width: '560px', maxWidth: '96vw', autoFocus: false })
          .afterClosed().subscribe((draft?: ExpenseDraft) => {
            if (draft) {
              this.apply(expense ? this.expenseService.update(expense.id, draft) : this.expenseService.create(draft),
                expense ? 'expenses.saved' : 'expenses.created');
            }
          });
      },
      error: () => this.notifications.error('expenses.loadError')
    });
  }

  remove(expense: Expense): void {
    this.confirm.confirm({ messageKey: 'expenses.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (confirmed) {
        this.apply(this.expenseService.delete(expense.id), 'expenses.deleted');
      }
    });
  }

  private apply(request: Observable<unknown>, successKey: string): void {
    request.subscribe({
      next: () => {
        this.notifications.success(successKey);
        this.load();
      },
      error: () => this.notifications.error('expenses.saveError')
    });
  }

  private load(): void {
    this.loading = true;
    forkJoin({ expenses: this.expenseService.list(this.year), summary: this.expenseService.summary(this.year) }).subscribe({
      next: ({ expenses, summary }) => {
        this.expenses = expenses;
        this.summary = summary;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.notifications.error('expenses.loadError');
      }
    });
  }
}
