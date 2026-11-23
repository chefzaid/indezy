import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { RevenueCeilings } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { RevenueCeilingsComponent } from './revenue-ceilings.component';

describe('RevenueCeilingsComponent', () => {
  const none: RevenueCeilings = { year: 2026, figuresYear: 2026, legalForm: 'SASU', vatExempt: false, received: 50000,
    receivedLastYear: 0, outstanding: 0, micro: null, vat: null };

  function create(ceilings: RevenueCeilings): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [RevenueCeilingsComponent, TranslateModule.forRoot()],
      providers: [{ provide: InvoiceService, useValue: { ceilings: () => of(ceilings) } }]
    });
    const fixture = TestBed.createComponent(RevenueCeilingsComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows nothing when no ceiling applies', () => {
    expect(create(none).querySelector('[data-revenue-ceilings]')).toBeNull();
  });

  it('measures the micro-entreprise ceiling and the franchise, warning past one', () => {
    const element = create({ ...none, legalForm: 'MICRO_ENTREPRISE', vatExempt: true, received: 42000, outstanding: 5000,
      micro: { limit: 83600, tolerance: null, revenue: 42000, percent: 50, level: 'UNDER', endsOn: null },
      vat: { limit: 37500, tolerance: 41250, revenue: 42000, percent: 112, level: 'ENDS', endsOn: '2026-09-30' } });
    const micro = element.querySelector('[data-ceiling="micro"]')!;
    expect(micro.textContent).toContain('50 %');
    expect(micro.querySelector('[data-ceiling-warning]')).toBeNull();
    const vat = element.querySelector('[data-ceiling="vat"]')!;
    expect(vat.querySelector('[data-ceiling-warning]')!.textContent).toContain('invoices.ceilings.vatEndsOnDay');
    expect((vat.querySelector('.bar span') as HTMLElement).style.width).toBe('100%');
    expect(element.querySelector('[data-ceiling-outstanding]')).not.toBeNull();
  });

  it('says when the franchise already ended or ends next year', () => {
    create(none);
    const component = TestBed.createComponent(RevenueCeilingsComponent).componentInstance;
    const ceiling = { limit: 37500, tolerance: 41250, revenue: 0, percent: 0, level: 'ENDS' as const, endsOn: '2026-01-01' };
    expect(component.endKind(ceiling, 2026)).toBe('past');
    expect(component.endKind({ ...ceiling, endsOn: '2026-05-04' }, 2026)).toBe('day');
    expect(component.endKind({ ...ceiling, endsOn: '2027-01-01' }, 2026)).toBe('next');
  });
});
