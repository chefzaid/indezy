import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Chart } from 'chart.js';
import { OfferBarChartComponent } from './offer-bar-chart.component';

describe('OfferBarChartComponent', () => {
  function create(highlight: number | null): ComponentFixture<OfferBarChartComponent> {
    const fixture = TestBed.createComponent(OfferBarChartComponent);
    fixture.componentRef.setInput('labels', ['500–550 €', '550–600 €', '600–650 €']);
    fixture.componentRef.setInput('values', [3, 8, 5]);
    fixture.componentRef.setInput('description', 'TJM proposés; 500–550 €: 3 offres');
    fixture.componentRef.setInput('highlight', highlight);
    fixture.detectChanges();
    return fixture;
  }

  function chartOf(fixture: ComponentFixture<OfferBarChartComponent>): Chart {
    return Chart.getChart(fixture.nativeElement.querySelector('canvas') as HTMLCanvasElement) as Chart;
  }

  it('draws the values with the highlighted bar apart and describes them to screen readers', () => {
    const fixture = create(1);
    const dataset = chartOf(fixture).data.datasets[0];

    expect(fixture.nativeElement.querySelector('canvas').getAttribute('aria-label')).toBe('TJM proposés; 500–550 €: 3 offres');
    expect(dataset.data).toEqual([3, 8, 5]);
    const colors = dataset.backgroundColor as string[];
    expect(colors[1]).not.toBe(colors[0]);
    expect(colors[2]).toBe(colors[0]);
  });

  it('redraws on new values and lets the chart go when removed', () => {
    const fixture = create(null);
    const first = chartOf(fixture);

    fixture.componentRef.setInput('values', [1, 2, 3]);
    fixture.detectChanges();
    const second = chartOf(fixture);

    expect(second).not.toBe(first);
    expect(second.data.datasets[0].data).toEqual([1, 2, 3]);
    expect(new Set(second.data.datasets[0].backgroundColor as string[]).size).toBe(1);
    fixture.destroy();
    expect(Chart.getChart(second.canvas)).toBeUndefined();
  });
});
