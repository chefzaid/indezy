import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { PurchaseOrder, PurchaseOrderWrite } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { PurchaseOrderService } from '../../../services/purchase-order/purchase-order.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { PurchaseOrdersCardComponent } from './purchase-orders-card.component';
import en from '../../../../assets/i18n/en.json';

describe('PurchaseOrdersCardComponent', () => {
  let service: jasmine.SpyObj<PurchaseOrderService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const write: PurchaseOrderWrite = { amendsId: null, number: 'BC', periodStart: '2026-09-01', periodEnd: null, dayCeiling: 10,
    amountCeiling: null, notes: null };
  const order = (id: number, changes: Partial<PurchaseOrder['usage']> = {}, amendments: PurchaseOrder['amendments'] = []): PurchaseOrder => ({
    id, projectId: 2, number: `BC-${id}`, periodStart: '2026-09-01', periodEnd: null, dayCeiling: 4, amountCeiling: 2000, notes: 'Signed',
    amendments,
    usage: { dayCeiling: 4, amountCeiling: 2000, periodEnd: null, workedDays: 3.5, amount: 1750, share: 0.875, daysLeft: 0.5, level: 'NEAR',
      ...changes }
  });
  const closing = (result: unknown) => dialog.open.and.returnValue({ afterClosed: () => of(result) } as MatDialogRef<unknown>);

  function create(list = of([order(1, {}, [{ id: 5, number: 'Avenant 1', periodEnd: '2027-01-31', dayCeiling: 2, amountCeiling: 500,
    notes: null }])])): ComponentFixture<PurchaseOrdersCardComponent> {
    service = jasmine.createSpyObj('PurchaseOrderService', ['list', 'create', 'update', 'delete']);
    service.list.and.returnValue(list);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [PurchaseOrdersCardComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: PurchaseOrderService, useValue: service }, { provide: ConfirmDialogService, useValue: confirm },
        { provide: NotificationService, useValue: notifications }]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(PurchaseOrdersCardComponent);
    fixture.componentRef.setInput('projectId', 2);
    fixture.detectChanges();
    return fixture;
  }

  it('shows each order with what was used, its alert and its amendments', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(service.list).toHaveBeenCalledWith(2);
    const text = element.querySelector('[data-order="1"]')!.textContent!;
    expect(text).toContain('3.5 of 4 days used');
    expect(text).toContain('€1,750 of €2,000 used');
    expect(text).toContain('0.5 days left');
    expect(text).toContain('open-ended');
    expect(element.querySelector('[data-order-alert]')!.textContent).toContain('80 % of the ceiling used');
    expect(element.querySelector('[data-amendment="5"]')!.textContent).toContain('+2 days');
    expect(element.querySelector('mat-progress-bar')!.getAttribute('aria-label')).toBe('87 % used');

    fixture.componentInstance.orders = [order(3, { amount: null, share: null, daysLeft: -1, level: 'OK', dayCeiling: null })];
    fixture.detectChanges();
    const other = element.querySelector('[data-order="3"]')!.textContent!;
    expect(other).toContain('Set the mission\'s daily rate');
    expect(other).toContain('1 days over');
    expect(element.querySelector('mat-progress-bar')).toBeNull();
    expect(fixture.componentInstance.percent(order(3, { share: null }))).toBeNull();
  });

  it('says when there is none, or when they cannot be loaded', () => {
    const empty = create(of([]));
    expect((empty.nativeElement as HTMLElement).textContent).toContain('No purchase order yet');
    TestBed.resetTestingModule();
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('purchaseOrders.loadError');
  });

  it('adds an order, an amendment, and changes both', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const updated = order(1, { level: 'OK' });
    service.create.and.returnValue(of(order(6)));
    closing(write);
    component.add();
    expect(service.create).toHaveBeenCalledWith(2, write);
    expect(component.orders.map(item => item.id)).toEqual([1, 6]);
    expect(notifications.success).toHaveBeenCalledWith('purchaseOrders.created');

    service.create.and.returnValue(of(updated));
    const amended = component.orders[0];
    component.amend(amended);
    expect(dialog.open.calls.mostRecent().args[1]!.data).toEqual({ amends: amended });
    expect(component.orders[0]).toEqual(updated);

    service.update.and.returnValue(of(updated));
    component.edit(updated);
    expect(service.update).toHaveBeenCalledWith(1, write);
    component.editAmendment(updated, { id: 5, number: 'A', periodEnd: null, dayCeiling: 1, amountCeiling: null, notes: null });
    expect(service.update).toHaveBeenCalledWith(5, write);

    closing(undefined);
    component.add();
    expect(service.create).toHaveBeenCalledTimes(2);
    closing(write);
    service.create.and.returnValue(throwError(() => new Error('down')));
    component.add();
    expect(notifications.error).toHaveBeenCalledWith('purchaseOrders.saveError');
  });

  it('deletes once confirmed, then reloads', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    service.delete.and.returnValue(of(undefined));
    confirm.confirm.and.returnValue(of(false));
    component.remove(1, 'BC-1', true);
    expect(service.delete).not.toHaveBeenCalled();

    confirm.confirm.and.returnValue(of(true));
    component.remove(1, 'BC-1', true);
    expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageKey: 'purchaseOrders.deleteWithAmendments',
      messageParams: { number: 'BC-1' } }));
    expect(service.delete).toHaveBeenCalledWith(1);
    expect(service.list).toHaveBeenCalledTimes(2);
    component.remove(5, 'Avenant 1', false);
    expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageKey: 'purchaseOrders.deleteMessage' }));

    service.delete.and.returnValue(throwError(() => new Error('down')));
    component.remove(5, 'Avenant 1', false);
    expect(notifications.error).toHaveBeenCalledWith('purchaseOrders.saveError');
  });
});
