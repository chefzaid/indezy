import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ActivityDay } from '../../../models';
import { ActivityHeatmapComponent } from './activity-heatmap.component';
import en from '../../../../assets/i18n/en.json';
import fr from '../../../../assets/i18n/fr.json';

describe('ActivityHeatmapComponent', () => {
  let fixture: ComponentFixture<ActivityHeatmapComponent>;
  let component: ActivityHeatmapComponent;
  const today = new Date(2026, 10, 17); // Tue 17 Nov 2026
  const activity: ActivityDay[] = [
    { date: '2026-11-14', count: 3, opportunities: 2, interviews: 1, items: [
      { kind: 'OPPORTUNITY', label: 'Architecte Kafka', client: 'Banque Martin', projectId: 42 },
      { kind: 'OPPORTUNITY', label: null, client: null, projectId: null },
      { kind: 'INTERVIEW', label: 'Entretien technique', client: 'Banque Martin', projectId: 42 }] },
    { date: '2026-11-15', count: 1, opportunities: 1, interviews: 0, items: [] },
    { date: '2026-11-16', count: 8, opportunities: 0, interviews: 1, items: [
      { kind: 'INTERVIEW', label: 'Appel', client: null, projectId: 7 }] }
  ];

  function create(language = 'en', days: ActivityDay[] = activity): HTMLElement {
    TestBed.configureTestingModule({ imports: [ActivityHeatmapComponent, TranslateModule.forRoot()], providers: [provideRouter([])] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.setTranslation('fr', fr);
    translate.use(language);
    fixture = TestBed.createComponent(ActivityHeatmapComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('activity', days);
    fixture.componentRef.setInput('today', today);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  const cell = (element: HTMLElement, date: string): HTMLButtonElement => element.querySelector(`[data-heatmap-day="${date}"]`)!;
  const card = (): HTMLElement | null => document.querySelector('[data-heatmap-card]');

  afterEach(() => card()?.closest('.cdk-overlay-container')?.replaceChildren());

  it('maps counts to 0-4 intensity levels', () => {
    create();
    expect(component.intensity(0)).toBe(0);
    expect(component.intensity(2)).toBe(1);
    expect(component.intensity(4)).toBe(2);
    expect(component.intensity(6)).toBe(3);
    expect(component.intensity(20)).toBe(4);
  });

  it('builds about a year of Monday-to-Sunday weeks ending today, each day knowing its place', () => {
    create();
    const weeks = component.buildWeeks([{ date: '2026-06-30', count: 3 }], new Date(2026, 6, 1));
    weeks.slice(0, -1).forEach(week => expect(week.length).toBe(7));
    expect(weeks.length).toBeGreaterThanOrEqual(52);
    expect(weeks.length).toBeLessThanOrEqual(54);
    const found = weeks.flat().find(day => day.date === '2026-06-30')!;
    expect(found.count).toBe(3);
    expect(found.opportunities).toBe(0);
    expect(found.items).toEqual([]);
    expect(found.weekday).toBe(1);
    expect(weeks.flat().find(day => day.date === '2026-06-29')?.count).toBe(0);
  });

  it('labels the months and weekdays, and sums up the year', () => {
    const element = create();
    expect(component.weekdays).toEqual(['Mon', '', 'Wed', '', 'Fri', '', '']);
    expect(component.months.length).toBeGreaterThanOrEqual(11);
    expect(component.months.at(-1)?.name).toBe('Nov');
    const summary = element.querySelector('[data-heatmap-summary]')!.textContent!;
    expect(summary).toContain('12 activities in the last year');
    expect(summary).toContain('3 days with activity');
    expect(summary).toContain('Longest streak: 3 days');
    expect(summary).toContain('(8)');
    expect(cell(element, '2026-11-14').getAttribute('aria-label')).toBe('Saturday, November 14, 2026: 3 activities');
    expect(cell(element, '2026-11-15').getAttribute('aria-label')).toContain(': 1 activity');
    expect(cell(element, '2026-11-17').getAttribute('aria-label')).toContain(': No activity');
    expect(cell(element, '2026-11-17').tabIndex).toBe(0);
    expect(cell(element, '2026-11-16').tabIndex).toBe(-1);
  });

  it('writes French with French names', () => {
    const element = create('fr');
    expect(component.weekdays[0]).toBe('lun.');
    expect(element.querySelector('[data-heatmap-summary]')!.textContent).toContain('12 activités sur les 12 derniers mois');
    expect(component.split(component.cells.find(day => day.date === '2026-11-14')!)).toBe('2 opportunités · 1 entretien');
  });

  it('tells what happened on a day when hovered, and keeps it open when clicked, its opportunities a click away', () => {
    const element = create();
    const busy = cell(element, '2026-11-14');
    busy.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(busy.classList).toContain('active');
    expect(card()?.textContent).toContain('3 activities');
    expect(card()?.textContent).toContain('2 opportunities · 1 interview');
    expect(card()?.textContent).toContain('Architecte Kafka');
    expect(card()?.textContent).toContain('Untitled');
    expect(card()?.textContent).toContain('Click to open its opportunities.');
    expect(card()?.querySelector('[data-heatmap-item]')).toBeNull();
    busy.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(card()).toBeNull();

    busy.click();
    fixture.detectChanges();
    expect(busy.getAttribute('aria-expanded')).toBe('true');
    expect(card()?.getAttribute('role')).toBe('dialog');
    expect(card()?.querySelectorAll('[data-heatmap-item="42"]').length).toBe(2);
    // Another day hovered does not take the card away.
    cell(element, '2026-11-15').dispatchEvent(new MouseEvent('mouseenter'));
    busy.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(card()?.textContent).toContain('3 activities');
    (card()!.querySelector('[data-heatmap-close]') as HTMLButtonElement).click();
    fixture.detectChanges();
    // The focus goes back to the day, which shows its preview again, no longer kept open.
    expect(document.activeElement).toBe(busy);
    expect(card()?.getAttribute('role')).toBe('tooltip');
    expect(component.pinned).toBeFalse();
    busy.blur();
    fixture.detectChanges();
    expect(card()).toBeNull();

    busy.click();
    busy.click();
    fixture.detectChanges();
    expect(component.pinned).toBeFalse();
  });

  it('says when nothing happened, and counts what the list leaves out', () => {
    const element = create();
    cell(element, '2026-11-17').dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(card()?.textContent).toContain('No opportunity created, no interview held.');
    component.close();
    cell(element, '2026-11-16').dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(card()?.textContent).toContain('8 activities');
    expect(card()?.textContent).toContain('1 interview');
    expect(card()?.querySelector('.counts')?.textContent).not.toContain('opportunit');
    expect(card()?.textContent).toContain('and 7 more');
  });

  it('moves between days with the arrows, Home and End, and closes with Escape', fakeAsync(() => {
    const element = create();
    const start = cell(element, '2026-11-14');
    start.focus();
    const press = (key: string, on: HTMLElement): void => {
      on.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
      fixture.detectChanges();
    };
    press('ArrowDown', start);
    expect(document.activeElement).toBe(cell(element, '2026-11-15'));
    press('ArrowUp', cell(element, '2026-11-15'));
    press('ArrowLeft', start);
    expect(document.activeElement).toBe(cell(element, '2026-11-07'));
    press('ArrowRight', cell(element, '2026-11-07'));
    expect(document.activeElement).toBe(start);
    press('ArrowRight', start);
    expect(document.activeElement).toBe(cell(element, '2026-11-17'));
    press('Home', cell(element, '2026-11-17'));
    expect(document.activeElement).toBe(element.querySelector('[data-heatmap-day]'));
    press('End', document.activeElement as HTMLElement);
    expect(document.activeElement).toBe(cell(element, '2026-11-17'));
    press('Tab', cell(element, '2026-11-17'));
    start.click();
    fixture.detectChanges();
    expect(component.pinned).toBeTrue();
    press('Escape', start);
    expect(component.pinned).toBeFalse();
    expect(document.activeElement).toBe(start);
    tick();
  }));

  it('shows an empty year without a busiest day', fakeAsync(() => {
    const element = create('en', []);
    tick();
    expect(element.querySelector('[data-heatmap-summary]')!.textContent).toContain('No activity in the last year');
    expect(component.busiest).toBeNull();
    expect(component.icon({ kind: 'INTERVIEW', label: null, client: null, projectId: null })).toBe('event');
  }));
});
