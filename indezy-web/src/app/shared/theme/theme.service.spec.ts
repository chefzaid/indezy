import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    localStorage.removeItem('indezy-theme');
    service = TestBed.inject(ThemeService);
  });

  afterEach(() => {
    document.documentElement.classList.remove('dark-theme');
    localStorage.removeItem('indezy-theme');
  });

  it('switches the document to the dark theme and remembers it', () => {
    service.apply('dark');
    expect(document.documentElement.classList).toContain('dark-theme');
    expect(service.isDark()).toBeTrue();
    expect(localStorage.getItem('indezy-theme')).toBe('dark');
  });

  it('falls back to light for unknown values', () => {
    service.apply('dark');
    service.apply('sepia');
    expect(localStorage.getItem('indezy-theme')).toBe('light');
    expect(document.documentElement.classList).not.toContain('dark-theme');
  });

  it('restores the saved preference', () => {
    localStorage.setItem('indezy-theme', 'dark');
    service.restore();
    expect(service.isDark()).toBeTrue();
  });
});
