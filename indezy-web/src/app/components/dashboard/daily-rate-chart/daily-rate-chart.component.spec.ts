import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Chart } from 'chart.js';
import { DailyRateChartComponent } from './daily-rate-chart.component';

describe('DailyRateChartComponent', () => {
  let fixture: ComponentFixture<DailyRateChartComponent>;

  async function create(locale = 'en-US'): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [DailyRateChartComponent, TranslateModule.forRoot()],
      providers: [{ provide: LOCALE_ID, useValue: locale }]
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      dashboard: { charts: { dailyRateDistribution: 'Daily rate distribution', projects: 'Projects' } }
    });
    translate.use('en');
    fixture = TestBed.createComponent(DailyRateChartComponent);
  }

  function canvas(): HTMLCanvasElement {
    return fixture.nativeElement.querySelector('canvas');
  }

  it('plots distinct negotiated rates and exposes their counts to screen readers', async () => {
    await create();
    fixture.componentRef.setInput('rates', [
      { label: '550', count: 1 }, { label: '600', count: 2 }, { label: '650', count: 1 }
    ]);
    fixture.detectChanges();

    const chart = Chart.getChart(canvas())!;
    expect(chart.data.labels).toEqual(['€550', '€600', '€650']);
    expect(chart.data.datasets[0].data).toEqual([1, 2, 1]);
    expect(canvas().getAttribute('role')).toBe('img');
    expect(canvas().getAttribute('aria-label')).toBe(
      'Daily rate distribution; €550: 1 Projects; €600: 2 Projects; €650: 1 Projects'
    );
  });

  it('formats euro amounts using the French locale', async () => {
    await create('fr-FR');
    fixture.componentRef.setInput('rates', [{ label: '1200', count: 1 }]);
    fixture.detectChanges();
    expect(Chart.getChart(canvas())!.data.labels).toEqual(['1\u202f200\u00a0€']);
  });

  it('replaces the chart when a season changes, including an empty season', async () => {
    await create();
    fixture.componentRef.setInput('rates', [{ label: '600', count: 2 }]);
    fixture.detectChanges();
    const previous = Chart.getChart(canvas())!;

    fixture.componentRef.setInput('rates', []);
    fixture.detectChanges();
    const current = Chart.getChart(canvas())!;
    expect(current).not.toBe(previous);
    expect(previous.canvas).toBeNull();
    expect(current.data.labels).toEqual([]);
    expect(current.data.datasets[0].data).toEqual([]);
    expect(canvas().getAttribute('aria-label')).toBe('Daily rate distribution');

    const element = canvas();
    fixture.destroy();
    expect(Chart.getChart(element)).toBeUndefined();
  });

  it('keeps old API bucket labels readable during a rolling deployment', async () => {
    await create();
    fixture.componentRef.setInput('rates', [{ label: '500-700', count: 2 }]);
    fixture.detectChanges();
    expect(Chart.getChart(canvas())!.data.labels).toEqual(['500-700 €']);
  });
});
