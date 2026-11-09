import {
  AfterViewInit, Component, ElementRef, Input, LOCALE_ID, NgZone,
  OnChanges, OnDestroy, ViewChild, inject
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Chart, registerables } from 'chart.js';
import { DailyRateRange } from '../../../models';
import { applyChartTheme, themeColor } from '../../../shared/charts/chart-theme';

Chart.register(...registerables);

/** Exact negotiated rates, with the same localized values on the axis and for screen readers. */
@Component({
  selector: 'app-daily-rate-chart',
  template: '<canvas #canvas role="img" [attr.aria-label]="description"></canvas>',
  styles: ':host { display: block; height: 100%; }'
})
export class DailyRateChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) rates: DailyRateRange[] = [];
  @ViewChild('canvas') private canvas?: ElementRef<HTMLCanvasElement>;

  private readonly translate = inject(TranslateService);
  private readonly zone = inject(NgZone);
  private readonly currency = new Intl.NumberFormat(inject(LOCALE_ID), {
    style: 'currency', currency: 'EUR', maximumFractionDigits: 0
  });
  private chart?: Chart;

  get description(): string {
    const title = this.translate.instant('dashboard.charts.dailyRateDistribution');
    const projects = this.translate.instant('dashboard.charts.projects');
    return [title, ...this.rates.map(rate => `${this.label(rate)}: ${rate.count} ${projects}`)].join('; ');
  }

  ngAfterViewInit(): void {
    this.render();
  }

  ngOnChanges(): void {
    this.render();
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private label(rate: DailyRateRange): string {
    const amount = Number(rate.label);
    // Keep older API range labels readable while frontend and backend releases roll out.
    return Number.isFinite(amount) ? this.currency.format(amount) : `${rate.label} €`;
  }

  private render(): void {
    const canvas = this.canvas?.nativeElement;
    if (!canvas) {
      return;
    }
    this.chart?.destroy();
    applyChartTheme();
    const backgroundColor = themeColor('--app-chart-bar', 'rgba(79, 70, 229, 0.75)');
    const borderColor = themeColor('--app-chart-bar-border', '#4f46e5');
    this.zone.runOutsideAngular(() => {
      this.chart = new Chart(canvas, {
        type: 'bar',
        data: {
          labels: this.rates.map(rate => this.label(rate)),
          datasets: [{
            label: this.translate.instant('dashboard.charts.projects'),
            data: this.rates.map(rate => rate.count),
            backgroundColor,
            borderColor,
            borderWidth: 1,
            borderRadius: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            x: { grid: { display: false } },
            y: { beginAtZero: true, ticks: { stepSize: 1 } }
          },
          plugins: { legend: { display: false } }
        }
      });
    });
  }
}
