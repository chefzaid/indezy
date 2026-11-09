import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, NgZone,
  OnChanges, OnDestroy, ViewChild, inject
} from '@angular/core';
import { Chart, registerables } from 'chart.js';
import { applyChartTheme, themeColor } from '../../../shared/charts/chart-theme';

Chart.register(...registerables);

/** A small bar chart of the market snapshot; screen readers get its values from `description`. */
@Component({
  selector: 'app-offer-bar-chart',
  template: '<canvas #canvas role="img" [attr.aria-label]="description"></canvas>',
  styles: ':host { display: block; position: relative; height: 180px; }',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferBarChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) labels: string[] = [];
  @Input({ required: true }) values: number[] = [];
  @Input({ required: true }) description = '';
  @Input() seriesLabel = '';
  /** A bar drawn in the accent colour, such as the band of the target daily rate. */
  @Input() highlight: number | null = null;
  @ViewChild('canvas') private canvas?: ElementRef<HTMLCanvasElement>;

  private readonly zone = inject(NgZone);
  private chart?: Chart;

  ngAfterViewInit(): void {
    this.render();
  }

  ngOnChanges(): void {
    this.render();
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private render(): void {
    const canvas = this.canvas?.nativeElement;
    if (!canvas) {
      return;
    }
    this.chart?.destroy();
    applyChartTheme();
    const bar = themeColor('--app-chart-bar', 'rgba(79, 70, 229, 0.75)');
    const barBorder = themeColor('--app-chart-bar-border', '#4f46e5');
    const highlight = themeColor('--app-chart-highlight', 'rgba(217, 119, 6, 0.85)');
    const highlightBorder = themeColor('--app-chart-highlight-border', '#d97706');
    const highlighted = (index: number): boolean => index === this.highlight;
    this.zone.runOutsideAngular(() => {
      this.chart = new Chart(canvas, {
        type: 'bar',
        data: {
          labels: this.labels,
          datasets: [{
            label: this.seriesLabel,
            data: this.values,
            backgroundColor: this.values.map((_, index) => highlighted(index) ? highlight : bar),
            borderColor: this.values.map((_, index) => highlighted(index) ? highlightBorder : barBorder),
            borderWidth: 1,
            borderRadius: 4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            x: { ticks: { maxRotation: 0, autoSkip: true }, grid: { display: false } },
            y: { beginAtZero: true, ticks: { precision: 0 } }
          },
          plugins: { legend: { display: false } }
        }
      });
    });
  }
}
