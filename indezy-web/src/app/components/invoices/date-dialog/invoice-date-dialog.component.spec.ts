import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { InvoiceDateDialogComponent } from './invoice-date-dialog.component';

describe('InvoiceDateDialogComponent', () => {
  it('offers the date it is given, and refuses one past the latest allowed', async () => {
    TestBed.configureTestingModule({
      imports: [InvoiceDateDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: { titleKey: 't', messageKey: 'm', labelKey: 'l', confirmKey: 'c',
        date: '2026-10-27', max: '2026-10-27' } }]
    });
    const fixture = TestBed.createComponent(InvoiceDateDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const confirm = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-invoice-date-confirm]')!;
    expect(fixture.componentInstance.date).toBe('2026-10-27');
    expect(confirm.disabled).toBeFalse();

    fixture.componentInstance.date = '2026-10-28';
    fixture.detectChanges();
    expect(confirm.disabled).toBeTrue();
  });
});
