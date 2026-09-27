import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RateTargetComponent } from './rate-target.component';

describe('RateTargetComponent', () => {
  let fixture: ComponentFixture<RateTargetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RateTargetComponent, TranslateModule.forRoot()] }).compileComponents();
    fixture = TestBed.createComponent(RateTargetComponent);
    fixture.componentRef.setInput('target', 650);
  });

  it('compares the target with the obtained daily rate, formatted in euros', () => {
    fixture.componentRef.setInput('obtained', 625);
    fixture.detectChanges();
    const values = [...fixture.nativeElement.querySelectorAll('dd')].map(element => element.textContent.trim());
    expect(values).toEqual(['€650', '€625']);
  });

  it('distinguishes no accepted rate from a recorded zero rate', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('dd')[1].textContent.trim()).toBe('—');
    fixture.componentRef.setInput('obtained', 0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('dd')[1].textContent.trim()).toBe('€0');
  });
});
