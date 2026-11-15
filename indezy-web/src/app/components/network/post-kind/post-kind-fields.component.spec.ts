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

  it('takes two to four distinct choices for a poll, and its days', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    expect(component.poll()).toBeNull();
    component.kind.setValue('POLL');
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('[data-poll-choice]').length).toBe(2);
    expect(element.querySelector('[data-poll-remove]')).toBeNull();
    expect(component.valid).toBeFalse();
    component.choices.setValue(['IntelliJ', ' intellij ']);
    expect(component.valid).toBeFalse();
    component.choices.setValue(['IntelliJ', 'VS Code']);
    expect(component.valid).toBeTrue();
    (element.querySelector('[data-poll-add]') as HTMLButtonElement).click();
    component.addChoice();
    component.addChoice();
    fixture.detectChanges();
    expect(component.choices.length).toBe(4);
    expect(element.querySelector('[data-poll-add]')).toBeNull();
    component.choices.at(2).setValue(' Vim ');
    component.days.setValue(5);
    expect(component.poll()).toEqual({ choices: ['IntelliJ', 'VS Code', 'Vim'], days: 5 });
    (element.querySelector('[data-poll-remove="3"]') as HTMLButtonElement).click();
    component.removeChoice(0);
    component.removeChoice(0);
    expect(component.choices.length).toBe(2);
    component.reset();
    expect(component.choices.getRawValue()).toEqual(['', '']);
    expect(component.days.value).toBe(3);
  });

  it('starts from a kind and facts shared from elsewhere', () => {
    TestBed.configureTestingModule({
      imports: [PostKindFieldsComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }]
    });
    const fixture = TestBed.createComponent(PostKindFieldsComponent);
    fixture.componentRef.setInput('initial', { kind: 'MISSION', details: { role: 'Lead Java', rateMin: null, rateMax: 700, city: null,
      remoteDays: 2, start: null, client: null, stack: ['Java', 'Kafka'] } });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(component.kind.value).toBe('MISSION');
    expect(component.facts()).toEqual({ role: 'Lead Java', rateMin: null, rateMax: 700, city: null, remoteDays: 2, start: null,
      client: null, stack: ['Java', 'Kafka'] });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [PostKindFieldsComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }] });
    const plain = TestBed.createComponent(PostKindFieldsComponent);
    plain.componentRef.setInput('initial', { kind: 'QUESTION', details: null });
    plain.detectChanges();
    expect(plain.componentInstance.kind.value).toBe('QUESTION');
  });
});
