import { Chart } from 'chart.js';
import { applyChartTheme, themeColor } from './chart-theme';

describe('chart theme', () => {
  afterEach(() => {
    document.documentElement.classList.remove('dark-theme');
    document.documentElement.style.removeProperty('--test-colour');
  });

  it('resolves a light-dark() token for the theme on screen', () => {
    document.documentElement.style.setProperty('--test-colour', 'light-dark(#ffffff, #000000)');
    expect(themeColor('--test-colour', 'red')).toBe('rgb(255, 255, 255)');
    document.documentElement.classList.add('dark-theme');
    expect(themeColor('--test-colour', 'red')).toBe('rgb(0, 0, 0)');
  });

  it('falls back when the token is missing', () => {
    expect(themeColor('--no-such-colour', '#ff0000')).toBe('rgb(255, 0, 0)');
  });

  it('gives Chart.js the muted text, border colour and font of the page', () => {
    applyChartTheme();
    expect(Chart.defaults.color).toBe(themeColor('--app-text-muted', '#5e5d6d'));
    expect(Chart.defaults.borderColor).toBe(themeColor('--app-border', '#e4e1ee'));
    expect(Chart.defaults.font.family).toContain('Inter');
  });
});
