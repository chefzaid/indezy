import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { BilledDaysComponent } from './billed-days.component';

describe('BilledDaysComponent', () => {
  let fixture: ComponentFixture<BilledDaysComponent>;
  const text = (selector: string): string | undefined =>
    fixture.nativeElement.querySelector(selector)?.textContent.replace(/\s+/g, ' ').trim();

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [BilledDaysComponent, TranslateModule.forRoot()] }).compileComponents();
    fixture = TestBed.createComponent(BilledDaysComponent);
  });

  it('shows the billed days against the target and the shortfall at this pace', () => {
    fixture.componentRef.setInput('days', { year: 2026, billed: 42.5, target: 200, projected: 154 });
    fixture.detectChanges();
    expect(text('[data-billed-days-billed]')).toBe('42.5 / 200 (21 %)');
    expect(text('[data-billed-days-projected]')).toBe('154');
    expect(fixture.nativeElement.querySelector('[data-billed-days-gap]').classList).toContain('short');
  });

  it('marks a projection above the target as ahead', () => {
    fixture.componentRef.setInput('days', { year: 2026, billed: 120, target: 200, projected: 215 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-billed-days-gap]').classList).not.toContain('short');
  });

  it('shows only the billed days without a target or a projection', () => {
    fixture.componentRef.setInput('days', { year: 2026, billed: 5, target: null, projected: null });
    fixture.detectChanges();
    expect(text('[data-billed-days-billed]')).toBe('5');
    expect(text('[data-billed-days-projected]')).toBe('—');
    expect(fixture.nativeElement.querySelector('[data-billed-days-gap]')).toBeNull();
  });
});
