import { Chart } from 'chart.js';

/**
 * The colour a CSS token stands for in the current theme, as rgb() or rgba(). The tokens hold light-dark() pairs,
 * which getPropertyValue returns unresolved and Chart.js cannot read, so the colour is resolved on a hidden element.
 */
export function themeColor(token: string, fallback: string): string {
  const probe = document.createElement('span');
  probe.style.display = 'none';
  probe.style.color = `var(${token}, ${fallback})`;
  document.body.appendChild(probe);
  const color = getComputedStyle(probe).color;
  probe.remove();
  return color || fallback;
}

/** Chart.js's text, grid lines and font follow the page: call it before drawing a chart. */
export function applyChartTheme(): void {
  Chart.defaults.color = themeColor('--app-text-muted', '#5e5d6d');
  Chart.defaults.borderColor = themeColor('--app-border', '#e4e1ee');
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
}
