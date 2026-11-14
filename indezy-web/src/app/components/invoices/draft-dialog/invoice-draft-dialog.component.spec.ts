import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import { ClientDto, Invoice, ProjectDto, Quote } from '../../../models';
import { InvoiceDraftDialogComponent, InvoiceDraftDialogData } from './invoice-draft-dialog.component';

describe('InvoiceDraftDialogComponent', () => {
  let close: jasmine.Spy;
  const clients = [{ id: 3, companyName: 'Acme' }, { id: 4, companyName: 'Alten' }] as ClientDto[];
  const projects = [{ id: 8, role: 'Java lead', clientId: 3, middlemanId: 4 }, { id: 9, role: 'Architect', clientId: 3 }] as ProjectDto[];

  function create(data: Partial<InvoiceDraftDialogData> = {}): InvoiceDraftDialogComponent {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [InvoiceDraftDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: MAT_DIALOG_DATA, useValue: { clients, projects, vatRate: 20, ...data } },
        { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(InvoiceDraftDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('starts a new draft with one line at the workspace\'s VAT rate and counts its totals as the server does', () => {
    const component = create();
    expect(component.titleKey).toBe('invoices.newTitle');
    expect(component.lines.length).toBe(1);
    component.lines.at(0).patchValue({ description: 'Audit', quantity: 18.5, unitPrice: 633.33 });
    component.addLine();
    component.lines.at(1).patchValue({ description: 'Frais', quantity: 1, unitPrice: 0.05, vatRate: 5.5 });

    expect(component.totals.excludingVat).toBeCloseTo(11716.66, 2);
    expect(component.totals.vat).toBeCloseTo(2343.32, 2);
    expect(component.totals.total).toBeCloseTo(14059.98, 2);
    component.removeLine(1);
    expect(component.lines.length).toBe(1);
  });

  it('offers the missions of the chosen client, as client or ESN, and drops one that no longer fits', () => {
    const component = create();
    component.form.patchValue({ clientId: 4, projectId: 8 });
    expect(component.projects.map(project => project.id)).toEqual([8]);
    component.form.patchValue({ clientId: 3 });
    component.clientChanged();
    expect(component.form.value.projectId).toBe(8);
    component.form.patchValue({ clientId: 4, projectId: 9 });
    component.clientChanged();
    expect(component.form.value.projectId).toBeNull();
  });

  it('changes a draft and closes with it, trimmed', () => {
    const invoice = { id: 5, clientId: 3, projectId: 9, periodStart: '2026-10-01', periodEnd: '2026-10-31', language: 'EN', notes: 'PO 12',
      lines: [{ description: 'Audit', quantity: 2, unitPrice: 700, vatRate: 0 }] } as unknown as Invoice;
    const component = create({ invoice, vatRate: 0 });
    expect(component.titleKey).toBe('invoices.editTitle');
    expect(component.form.value.language).toBe('EN');
    component.lines.at(0).patchValue({ description: ' Audit SI ' });
    component.form.patchValue({ notes: ' ' });

    component.save();

    expect(close).toHaveBeenCalledWith({ clientId: 3, projectId: 9, periodStart: '2026-10-01', periodEnd: '2026-10-31', language: 'EN',
      notes: null, lines: [{ description: 'Audit SI', quantity: 2, unitPrice: 700, vatRate: 0 }] });
  });

  it('keeps a credit note\'s client', () => {
    const note = { id: 6, kind: 'CREDIT_NOTE', clientId: 3, projectId: null, language: 'FR',
      lines: [{ description: 'Audit', quantity: 1, unitPrice: 700, vatRate: 0 }] } as unknown as Invoice;
    const component = create({ invoice: note });
    expect(component.form.controls.clientId.disabled).toBeTrue();
    expect(component.titleKey).toBe('invoices.editCreditNote');
    component.save();
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ clientId: 3 }));
  });

  it('writes a quote with how long it holds and the deposit it asks, instead of a period', () => {
    const component = create({ forQuote: true });
    expect(component.titleKey).toBe('quotes.newTitle');
    expect(component.form.value.validityDays).toBe(30);
    component.form.patchValue({ clientId: 3, depositPercent: 101 });
    component.lines.at(0).patchValue({ description: 'Audit', unitPrice: 800 });
    component.save();
    expect(close).not.toHaveBeenCalled();
    component.form.patchValue({ depositPercent: null });
    component.save();
    expect(close).toHaveBeenCalledWith({ clientId: 3, projectId: null, language: 'FR', notes: null, validityDays: 30, depositPercent: null,
      lines: [{ description: 'Audit', quantity: 1, unitPrice: 800, vatRate: 20 }] });
  });

  it('changes a quote draft, keeping its validity and deposit', () => {
    const quote = { id: 5, clientId: 3, projectId: 9, language: 'EN', notes: 'Start in two weeks', validityDays: 15, depositPercent: 30,
      lines: [{ description: 'Audit', quantity: 5, unitPrice: 800, vatRate: 0 }] } as unknown as Quote;
    const component = create({ forQuote: true, quote });
    expect(component.titleKey).toBe('quotes.editTitle');
    component.save();
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ projectId: 9, language: 'EN', validityDays: 15, depositPercent: 30,
      notes: 'Start in two weeks' }));
  });

  it('keeps the dialog open while the draft is incomplete, and stops at fifty lines', () => {
    const component = create();
    component.save();
    expect(close).not.toHaveBeenCalled();
    // A negative quantity takes off; none bills nothing.
    component.form.patchValue({ clientId: 3 });
    component.lines.at(0).patchValue({ description: 'Remise', quantity: 0, unitPrice: 100 });
    component.save();
    expect(close).not.toHaveBeenCalled();
    component.lines.at(0).patchValue({ quantity: -1 });
    expect(component.totals.excludingVat).toBe(-100);
    component.save();
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ lines: [jasmine.objectContaining({ quantity: -1 })] }));
    close.calls.reset();
    for (let index = 1; index < 50; index++) {
      component.addLine();
    }
    expect(component.canAddLine).toBeFalse();
  });
});
