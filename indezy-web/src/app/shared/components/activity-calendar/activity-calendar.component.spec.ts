import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../../assets/i18n/en.json';
import fr from '../../../../assets/i18n/fr.json';
import { ActivityCalendarComponent } from './activity-calendar.component';

describe('ActivityCalendarComponent', () => {
  function render(language: 'fr' | 'en', editable: boolean): ComponentFixture<ActivityCalendarComponent> {
    registerLocaleData(localeFr);
    TestBed.configureTestingModule({
      imports: [ActivityCalendarComponent, TranslateModule.forRoot()],
      providers: [{ provide: LOCALE_ID, useValue: language === 'fr' ? 'fr-FR' : 'en-US' }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation(language, language === 'fr' ? fr : en);
    translate.use(language);
    const fixture = TestBed.createComponent(ActivityCalendarComponent);
    fixture.componentRef.setInput('year', 2026);
    fixture.componentRef.setInput('month', 11);
    fixture.componentRef.setInput('days', [
      { date: '2026-11-02', type: 'ONSITE', halfDay: false },
      { date: '2026-11-13', type: 'REMOTE', halfDay: true },
      { date: '2026-11-20', type: 'ABSENCE', halfDay: false }
    ]);
    fixture.componentRef.setInput('holidays', [{ date: '2026-11-11', holiday: 'ARMISTICE' }]);
    fixture.componentRef.setInput('missionStart', '2026-11-02');
    fixture.componentRef.setInput('editable', editable);
    fixture.detectChanges();
    return fixture;
  }

  function day(fixture: ComponentFixture<ActivityCalendarComponent>, date: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-date="${date}"]`);
  }

  it('shows the month Monday first with recorded days, holidays, weekends and days outside the mission', () => {
    const fixture = render('fr', false);
    const weekdays = [...fixture.nativeElement.querySelectorAll('.weekday')].map((cell: HTMLElement) => cell.textContent?.trim());

    expect(weekdays[0]).toBe('lun.');
    expect(fixture.nativeElement.querySelectorAll('.week').length).toBe(6);
    expect(day(fixture, '2026-11-02').getAttribute('data-type')).toBe('ONSITE');
    expect(day(fixture, '2026-11-13').classList).toContain('half');
    expect(day(fixture, '2026-11-11').classList).toContain('off');
    expect(day(fixture, '2026-11-11').textContent).toContain('Férié');
    expect(day(fixture, '2026-11-07').classList).toContain('off');
    expect(day(fixture, '2026-11-01').classList).toContain('outside');
    expect(fixture.nativeElement.querySelector('button.day')).toBeNull();
  });

  it('describes each day in French for screen readers', () => {
    const fixture = render('fr', false);

    expect(day(fixture, '2026-11-02').textContent).toContain('lundi 2 novembre 2026 : Journée sur site');
    expect(day(fixture, '2026-11-13').textContent).toContain('Demi-journée en télétravail');
    expect(day(fixture, '2026-11-11').textContent).toContain('non travaillé, Armistice 1918');
    expect(day(fixture, '2026-11-01').textContent).toContain('dimanche 1 novembre 2026 : non travaillé, week-end, hors mission');
    expect(day(fixture, '2026-11-08').textContent).toContain('week-end');
  });

  it('turns days into labelled buttons that emit their date when editable', () => {
    const fixture = render('en', true);
    const toggled: string[] = [];
    fixture.componentInstance.dayToggled.subscribe(date => toggled.push(date));

    const button = day(fixture, '2026-11-20') as HTMLButtonElement;
    expect(button.tagName).toBe('BUTTON');
    expect(button.getAttribute('aria-label')).toBe('Friday, November 20, 2026 : Absence');
    button.click();
    (day(fixture, '2026-11-11') as HTMLButtonElement).click();

    expect(toggled).toEqual(['2026-11-20', '2026-11-11']);
  });

  it('ignores toggles while read-only', () => {
    const fixture = render('en', false);
    const toggled: string[] = [];
    fixture.componentInstance.dayToggled.subscribe(date => toggled.push(date));

    fixture.componentInstance.toggle(fixture.componentInstance.weeks[1][0]!);

    expect(toggled).toEqual([]);
  });
});
