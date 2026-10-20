import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, NgZone,
  OnChanges, OnDestroy, ViewChild, inject
} from '@angular/core';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

const BAR = 'rgba(102, 126, 234, 0.7)';
const BAR_BORDER = '#667eea';
const HIGHLIGHT = 'rgba(245, 158, 11, 0.85)';
const HIGHLIGHT_BORDER = '#d97706';

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
    const styles = getComputedStyle(document.documentElement);
    const color = styles.getPropertyValue('--app-text-muted').trim() || '#666';
    const gridColor = styles.getPropertyValue('--app-border').trim() || 'rgba(0, 0, 0, 0.1)';
    const highlighted = (index: number): boolean => index === this.highlight;
    this.zone.runOutsideAngular(() => {
      this.chart = new Chart(canvas, {
        type: 'bar',
        data: {
          labels: this.labels,
          datasets: [{
            label: this.seriesLabel,
            data: this.values,
            backgroundColor: this.values.map((_, index) => highlighted(index) ? HIGHLIGHT : BAR),
            borderColor: this.values.map((_, index) => highlighted(index) ? HIGHLIGHT_BORDER : BAR_BORDER),
            borderWidth: 1,
            borderRadius: 4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            x: { ticks: { color, maxRotation: 0, autoSkip: true }, grid: { display: false } },
            y: { beginAtZero: true, ticks: { color, precision: 0 }, grid: { color: gridColor } }
          },
          plugins: { legend: { display: false } }
        }
      });
    });
  }
}
