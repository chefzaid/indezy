import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { ContributionEstimate } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { ContributionsComponent } from './contributions.component';

describe('ContributionsComponent', () => {
  const quarterly: ContributionEstimate = { year: 2026, figuresYear: 2026, legalForm: 'MICRO_ENTREPRISE', activity: 'BNC_SSI',
    incomeTaxDischarge: false, frequency: 'QUARTERLY', rate: 25.6, officialRate: true, revenue: 17000, contributions: 4352,
    periods: [
      { from: '2026-07-01', to: '2026-09-30', revenue: 0, contributions: 0, dueOn: '2026-10-31', state: 'PAST' },
      { from: '2026-10-01', to: '2026-12-31', revenue: 5000, contributions: 1280, dueOn: '2027-01-31', state: 'TO_DECLARE' }] };

  function create(estimate: ContributionEstimate): { element: HTMLElement; component: ContributionsComponent } {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ContributionsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: InvoiceService, useValue: { contributions: () => of(estimate) } }]
    });
    const fixture = TestBed.createComponent(ContributionsComponent);
    fixture.detectChanges();
    return { element: fixture.nativeElement as HTMLElement, component: fixture.componentInstance };
  }

  it('lists the declarations, the one to make now first, with the estimate at the official rate', () => {
    const { element, component } = create(quarterly);
    expect(element.querySelector('[data-contributions-summary]')!.textContent).toContain('invoices.contributions.official');
    const rows = element.querySelectorAll('[data-contribution-period]');
    expect(rows[0].getAttribute('data-contribution-period')).toBe('2026-10-01');
    expect(rows[0].classList).toContain('due');
    expect(rows[0].textContent).toContain('Q4');
    expect(rows[0].textContent).toContain('1,280 €');
    expect(component.quarter(quarterly.periods[0])).toBe('Q3');
  });

  it('shows an EI under the real regime its profit, the base and each contribution', () => {
    const { element } = create({ ...quarterly, legalForm: 'EI', rate: 23.5, revenue: 17000, contributions: 4000, periods: [],
      profit: 15000, base: 11100, lines: [{ kind: 'HEALTH', amount: 208.13 }, { kind: 'CSG_CRDS', amount: 1076.7 }] });
    expect(element.querySelector('[data-contributions-profit]')!.textContent).toContain('invoices.contributions.profit');
    expect(element.querySelector('[data-contribution-line="CSG_CRDS"]')!.textContent).toContain('1,077 €');
    expect(element.querySelector('[data-contributions-ei-basis]')).not.toBeNull();
    expect(element.querySelector('[data-contribution-period]')).toBeNull();
  });

  it('says when no rate is known, and stays away for a company without its own rate', () => {
    expect(create({ ...quarterly, rate: null, contributions: null, periods: [] }).element.querySelector('[data-contributions-no-rate]'))
      .not.toBeNull();
    expect(create({ ...quarterly, legalForm: 'SASU', rate: null, periods: [] }).element.querySelector('[data-contributions]')).toBeNull();
    expect(create({ ...quarterly, legalForm: 'SASU', rate: 45, officialRate: false, periods: [] }).element
      .querySelector('[data-contributions-summary]')!.textContent).toContain('invoices.contributions.own');
  });
});
