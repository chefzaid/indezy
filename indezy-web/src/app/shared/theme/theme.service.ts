import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ThemePreference = 'light' | 'dark' | 'auto';

const THEME_STORAGE_KEY = 'indezy-theme';
const DARK_CLASS = 'dark-theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

/**
 * Applies the user's theme preference (light, dark, or following the system) by toggling the
 * `dark-theme` class on the document root, where the dark Material theme and app tokens live.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService implements OnDestroy {
  private preference: ThemePreference = 'light';
  private readonly media = typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia(DARK_QUERY)
    : null;
  private readonly onSystemChange = (): void => this.render();
  private readonly darkSubject = new BehaviorSubject<boolean>(false);

  /** Emits whether the dark theme is currently displayed. */
  readonly isDark$ = this.darkSubject.asObservable();

  constructor() {
    this.media?.addEventListener('change', this.onSystemChange);
  }

  /** Applies the preference saved in this browser (called at startup, before any request). */
  restore(): void {
    let saved: string | null = null;
    try {
      saved = localStorage.getItem(THEME_STORAGE_KEY);
    } catch {
      // Storage unavailable: keep the light default.
    }
    this.apply(ThemeService.normalize(saved));
  }

  apply(preference: string | null | undefined): void {
    this.preference = ThemeService.normalize(preference);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, this.preference);
    } catch {
      // The theme still applies for this visit.
    }
    this.render();
  }

  getPreference(): ThemePreference {
    return this.preference;
  }

  isDark(): boolean {
    return this.darkSubject.value;
  }

  ngOnDestroy(): void {
    this.media?.removeEventListener('change', this.onSystemChange);
  }

  static normalize(value: string | null | undefined): ThemePreference {
    return value === 'dark' || value === 'auto' ? value : 'light';
  }

  private render(): void {
    const dark = this.preference === 'dark' || (this.preference === 'auto' && !!this.media?.matches);
    document.documentElement.classList.toggle(DARK_CLASS, dark);
    this.darkSubject.next(dark);
  }
}
