import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { PostKindFieldsComponent } from './post-kind-fields.component';
import en from '../../../../assets/i18n/en.json';

describe('PostKindFieldsComponent', () => {
  function create() {
    TestBed.configureTestingModule({
      imports: [PostKindFieldsComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(PostKindFieldsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('asks a mission for its role and an availability for its start, the rates in order', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(component.valid).toBeTrue();
    expect(component.facts()).toBeNull();
    expect(element.querySelector('[data-composer-facts]')).toBeNull();

    component.kind.setValue('MISSION');
    fixture.detectChanges();
    expect(element.querySelector('[data-fact="client"]')).not.toBeNull();
    expect(component.valid).toBeFalse();
    component.details.patchValue({ role: ' Lead Java ', rateMin: 700, rateMax: 600 });
    expect(component.valid).toBeFalse();
    component.details.patchValue({ rateMin: 600, rateMax: 700, city: 'Lyon', client: ' Acme ', stack: 'Java, , Kafka' });
    expect(component.valid).toBeTrue();
    expect(component.facts()).toEqual({ role: 'Lead Java', rateMin: 600, rateMax: 700, city: 'Lyon', remoteDays: null, start: null,
      client: 'Acme', stack: ['Java', 'Kafka'] });

    component.kind.setValue('AVAILABLE');
    fixture.detectChanges();
    expect(element.querySelector('[data-fact="client"]')).toBeNull();
    expect(component.valid).toBeFalse();
    component.details.patchValue({ start: '2027-02-01' });
    expect(component.valid).toBeTrue();
    expect(component.facts()?.client).toBeNull();
    expect(component.facts()?.start).toBe('2027-02-01');

    component.reset();
    expect(component.kind.value).toBe('POST');
    expect(component.details.getRawValue().role).toBe('');
  });
});
