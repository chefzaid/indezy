import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import { Expense, ProjectDto } from '../../../models';
import { ExpenseDialogComponent, ExpenseDialogData } from './expense-dialog.component';

describe('ExpenseDialogComponent', () => {
  let close: jasmine.Spy;
  const projects = [{ id: 8, role: 'Java lead' }] as ProjectDto[];

  function create(data: Partial<ExpenseDialogData> = {}): ExpenseDialogComponent {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [ExpenseDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: MAT_DIALOG_DATA, useValue: { projects, ...data } }, { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(ExpenseDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('writes a new expense, trimmed, dated today by default', () => {
    const component = create();
    expect(component.form.value.spentOn).toBe(new Date().toLocaleDateString('sv'));
    component.save();
    expect(close).not.toHaveBeenCalled();
    component.form.patchValue({ spentOn: '2026-03-12', label: ' Train to Lyon ', amount: 120, vat: 20, projectId: 8, notes: ' ' });
    component.save();
    expect(close).toHaveBeenCalledWith({ spentOn: '2026-03-12', label: 'Train to Lyon', category: 'TRAVEL', amount: 120, vat: 20,
      projectId: 8, notes: null });
  });

  it('refuses more VAT than the amount it is part of', () => {
    const component = create();
    component.form.patchValue({ label: 'Laptop', amount: 100, vat: 150 });
    expect(component.vatTooHigh).toBeTrue();
    component.save();
    expect(close).not.toHaveBeenCalled();
    component.form.patchValue({ vat: null });
    component.save();
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ vat: null, amount: 100 }));
  });

  it('changes an expense', () => {
    const expense: Expense = { id: 4, spentOn: '2026-02-01', label: 'Coworking', category: 'OFFICE', amount: 250, vat: 0, projectId: null,
      projectRole: null, notes: 'February' };
    const component = create({ expense });
    expect(component.form.value.vat).toBeNull();
    component.save();
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ label: 'Coworking', category: 'OFFICE', notes: 'February' }));
  });
});
