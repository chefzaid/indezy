import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import en from '../../../assets/i18n/en.json';
import { Expense, ExpenseSummary, ProjectDto, ProjectStatus } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { ExpenseService } from '../../services/expense/expense.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { ExpensesComponent } from './expenses.component';

describe('ExpensesComponent', () => {
  let expenses: jasmine.SpyObj<ExpenseService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const year = new Date().getFullYear();
  const train: Expense = { id: 1, spentOn: `${year}-03-12`, label: 'Train to Lyon', category: 'TRAVEL', amount: 120, vat: 20, projectId: 8,
    projectRole: 'Java lead', notes: null };
  const laptop: Expense = { id: 2, spentOn: `${year}-03-02`, label: 'Laptop', category: 'EQUIPMENT', amount: 1200, vat: 200, projectId: null,
    projectRole: null, notes: null };
  const months = Array.from({ length: 12 }, (_, index) => index === 2 ? 1320 : 0);
  const summary: ExpenseSummary = { year, legalForm: 'SASU', treatment: 'DEDUCTIBLE', total: 1320, vat: 220, deductible: 1100, netCost: 660,
    reversionRate: 60, months, byCategory: { TRAVEL: 120, EQUIPMENT: 1200 } };

  function create(): ComponentFixture<ExpensesComponent> {
    expenses = jasmine.createSpyObj('ExpenseService', ['list', 'summary', 'create', 'update', 'delete']);
    expenses.list.and.returnValue(of([train, laptop]));
    expenses.summary.and.returnValue(of(summary));
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ExpensesComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ExpenseService, useValue: expenses },
        { provide: ProjectService, useValue: { getByFreelanceId: () => of([{ id: 8, role: 'Java lead', status: ProjectStatus.WON },
          { id: 9, role: 'Lead', status: ProjectStatus.CONTACT }] as ProjectDto[]) } },
        { provide: AuthService, useValue: { getUser: () => ({ id: 7 }) } },
        { provide: MatDialog, useValue: dialog },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ExpensesComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the year\'s expenses, their totals by month and category, and what the status changes', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(expenses.list).toHaveBeenCalledWith(year);
    expect(element.querySelector('[data-expense-total]')?.textContent).toContain('1,320.00 €');
    expect(element.querySelector('[data-expense-net-cost]')?.textContent).toContain('660.00 €');
    expect(element.querySelector('[data-expense-treatment="DEDUCTIBLE"]')?.textContent?.replace(/\s+/g, ' '))
      .toContain('lower your taxable profit by 1,100.00 €. Since you keep 60 % of what you earn, they cost you 660.00 € net.');
    expect(fixture.componentInstance.categories.map(entry => entry.category)).toEqual(['EQUIPMENT', 'TRAVEL']);
    expect(fixture.componentInstance.barWidth(1320)).toBe(100);
    expect(fixture.componentInstance.barWidth(0)).toBe(0);
    expect(element.querySelectorAll('[data-expense-months] li').length).toBe(12);
    expect(element.querySelector('[data-expense="1"]')?.textContent).toContain('Java lead');
    expect(element.querySelector('[data-expense="1"]')?.textContent).toContain('of which VAT 20.00 €');
  });

  it('moves between years, never past this one', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.changeYear(-1);
    expect(expenses.list).toHaveBeenCalledWith(year - 1);
    expect(expenses.summary).toHaveBeenCalledWith(year - 1);
    expenses.list.and.returnValue(of([]));
    component.changeYear(-1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty')?.textContent).toContain(`No expense in ${year - 2}.`);
  });

  it('adds an expense among the won missions, changes and deletes one', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const draft = { spentOn: `${year}-04-01`, label: 'Book', category: 'TRAINING', amount: 40, vat: null, projectId: null, notes: null };
    dialog.open.and.returnValue({ afterClosed: () => of(draft) } as MatDialogRef<unknown>);
    expenses.create.and.returnValue(of({ ...train, id: 3 }));
    component.write();
    expect((dialog.open.calls.mostRecent().args[1] as { data: { projects: ProjectDto[] } }).data.projects.map(project => project.id)).toEqual([8]);
    expect(expenses.create).toHaveBeenCalledWith(draft as never);
    expect(notifications.success).toHaveBeenCalledWith('expenses.created');

    expenses.update.and.returnValue(of(train));
    component.write(train);
    expect(expenses.update).toHaveBeenCalledWith(1, draft as never);

    confirm.confirm.and.returnValue(of(false));
    component.remove(laptop);
    expect(expenses.delete).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    expenses.delete.and.returnValue(of(undefined));
    component.remove(laptop);
    expect(expenses.delete).toHaveBeenCalledWith(2);
    expect(notifications.success).toHaveBeenCalledWith('expenses.deleted');
  });

  it('tells a failure to load or to save', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    expenses.list.and.returnValue(throwError(() => new Error('down')));
    component.changeYear(-1);
    expect(notifications.error).toHaveBeenCalledWith('expenses.loadError');
    expect(component.loading).toBeFalse();
    dialog.open.and.returnValue({ afterClosed: () => of({ label: 'x' }) } as MatDialogRef<unknown>);
    expenses.create.and.returnValue(throwError(() => new Error('down')));
    component.write();
    expect(notifications.error).toHaveBeenCalledWith('expenses.saveError');
  });

  it('asks for the legal status while none is set', () => {
    const fixture = create();
    fixture.componentInstance.summary = { ...summary, legalForm: null, treatment: 'RECORDED', deductible: 0, netCost: 1320 };
    fixture.detectChanges();
    const effect: HTMLElement = fixture.nativeElement.querySelector('[data-expense-treatment="RECORDED"]');
    expect(effect.textContent).toContain('No legal status set');
    expect(effect.querySelector('a')?.getAttribute('href')).toBe('/profile?tab=billing');
  });
});
