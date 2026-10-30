import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { PurchaseOrder, PurchaseOrderAmendment } from '../../../../models';
import { PurchaseOrderDialogComponent, PurchaseOrderDialogData } from './purchase-order-dialog.component';
import en from '../../../../../assets/i18n/en.json';

describe('PurchaseOrderDialogComponent', () => {
  let close: jasmine.Spy;
  const order = {
    id: 4, number: 'BC-118', periodStart: '2026-09-01', periodEnd: null, dayCeiling: 40, amountCeiling: null, notes: 'Procurement'
  } as PurchaseOrder;

  function create(data: PurchaseOrderDialogData): PurchaseOrderDialogComponent {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [PurchaseOrderDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: MAT_DIALOG_DATA, useValue: data }, { provide: MatDialogRef, useValue: { close } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(PurchaseOrderDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('writes an order with a start and a ceiling, trimmed', () => {
    const component = create({});
    expect(component.titleKey).toBe('purchaseOrders.newTitle');
    component.save();
    expect(close).not.toHaveBeenCalled();
    expect(component.form.hasError('ceilingRequired')).toBeTrue();

    component.form.patchValue({ number: ' BC-200 ', periodStart: '2026-10-01', periodEnd: '2026-09-30', amountCeiling: 12000, notes: '  ' });
    expect(component.form.hasError('endBeforeStart')).toBeTrue();
    component.form.patchValue({ periodEnd: '' });
    component.save();
    expect(close).toHaveBeenCalledWith({ amendsId: null, number: 'BC-200', periodStart: '2026-10-01', periodEnd: null, dayCeiling: null,
      amountCeiling: 12000, notes: null });
  });

  it('changes an order as it was', () => {
    const component = create({ order });
    expect(component.titleKey).toBe('purchaseOrders.editTitle');
    component.save();
    expect(close).toHaveBeenCalledWith({ amendsId: null, number: 'BC-118', periodStart: '2026-09-01', periodEnd: null, dayCeiling: 40,
      amountCeiling: null, notes: 'Procurement' });
  });

  it('writes an amendment adding something, never ending before its order starts', () => {
    const component = create({ amends: order });
    expect(component.titleKey).toBe('purchaseOrders.amendTitle');
    component.form.patchValue({ number: 'Avenant 1' });
    expect(component.form.hasError('nothingAdded')).toBeTrue();
    component.form.patchValue({ periodEnd: '2026-08-31' });
    expect(component.form.hasError('endBeforeStart')).toBeTrue();
    component.form.patchValue({ periodEnd: '2027-03-31' });
    component.save();
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ amendsId: 4, number: 'Avenant 1', periodStart: null,
      periodEnd: '2027-03-31' }));
  });

  it('changes an amendment', () => {
    const amendment = { id: 9, number: 'Avenant 2', periodEnd: null, dayCeiling: 10, amountCeiling: null, notes: null } as PurchaseOrderAmendment;
    const component = create({ amendment, amends: order });
    expect(component.titleKey).toBe('purchaseOrders.editAmendmentTitle');
    expect(component.form.valid).toBeTrue();
  });
});
