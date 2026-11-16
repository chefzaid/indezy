import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';
import { RateTargetComponent } from './rate-target.component';

describe('RateTargetComponent', () => {
  let fixture: ComponentFixture<RateTargetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RateTargetComponent, TranslateModule.forRoot()], providers: [provideRouter([])] })
      .compileComponents();
    fixture = TestBed.createComponent(RateTargetComponent);
    fixture.componentRef.setInput('target', 650);
  });

  it('compares the target with the obtained daily rate, formatted in euros', () => {
    fixture.componentRef.setInput('obtained', 625);
    fixture.detectChanges();
    const values = [...fixture.nativeElement.querySelectorAll('dd')].map(element => element.textContent.trim());
    expect(values).toEqual(['€650', '€625']);
  });

  it('shares the rate obtained against the objective, once there is one', () => {
    TestBed.inject(TranslateService).setTranslation('en', { dashboard: { rateShare: 'Obtained {{obtained}} € for {{target}} €' } });
    TestBed.inject(TranslateService).use('en');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-share-rate]')).toBeNull();
    fixture.componentRef.setInput('obtained', 624.6);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-share-rate]')).not.toBeNull();
    expect(fixture.componentInstance.share()).toEqual({ text: 'Obtained 625 € for 650 €', kind: 'POST', details: null });
  });

  it('distinguishes no accepted rate from a recorded zero rate', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('dd')[1].textContent.trim()).toBe('—');
    fixture.componentRef.setInput('obtained', 0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('dd')[1].textContent.trim()).toBe('€0');
  });
});
