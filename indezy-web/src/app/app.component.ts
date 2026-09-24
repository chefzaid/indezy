import { Component, DestroyRef, Injector, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';

import { RouterOutlet, RouterModule } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from './services/auth/auth.service';
import { LANGUAGE_STORAGE_KEY, getSavedLanguage } from './shared/locale/app-locale';
import { ThemeService } from './shared/theme/theme.service';
import { UserManagementService } from './services/user-management/user-management.service';

interface MenuItem {
  labelKey: string;
  route: string;
  icon: string;
}

@Component({
    selector: 'app-root',
    imports: [
    RouterOutlet,
    RouterModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatSidenavModule,
    MatListModule,
    MatMenuModule,
    MatTooltipModule,
    TranslateModule
],
    templateUrl: './app.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  title = 'Indezy';
  currentUser: { id: number; email: string; firstName: string; lastName: string; role?: string } | null = null;
  currentLang = 'fr';
  /** Below this width the navigation becomes an overlay drawer instead of a permanent side panel. */
  static readonly COMPACT_LAYOUT_QUERY = '(max-width: 1023.98px)';
  isCompactLayout = false;

  private readonly destroyRef = inject(DestroyRef);
  private readonly breakpointObserver = inject(BreakpointObserver);
  // Resolved on demand: the service loads the profile when created, which must not happen
  // on public pages such as login and registration.
  private readonly injector = inject(Injector);
  readonly themeService = inject(ThemeService);
  /** Account id whose saved theme preference has been applied. */
  private themeSyncedFor: number | null = null;

  menuItems: MenuItem[] = [
    { labelKey: 'nav.dashboard', route: '/dashboard', icon: 'dashboard' },
    { labelKey: 'nav.timeline', route: '/timeline', icon: 'event' },
    { labelKey: 'nav.projects', route: '/projects', icon: 'work' },
    { labelKey: 'nav.clients', route: '/clients', icon: 'business' },
    { labelKey: 'nav.contacts', route: '/contacts', icon: 'contacts' },
    { labelKey: 'nav.sources', route: '/sources', icon: 'source' },
    { labelKey: 'nav.archive', route: '/archive', icon: 'inventory_2' }
  ];

  constructor(
    public authService: AuthService,
    private readonly translate: TranslateService
  ) {
    this.currentLang = getSavedLanguage();
    this.translate.use(this.currentLang);
  }

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      this.syncThemeFromAccount();
    });
    this.breakpointObserver.observe(AppComponent.COMPACT_LAYOUT_QUERY)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(state => this.isCompactLayout = state.matches);
  }

  switchLanguage(lang: string): void {
    if (lang === this.currentLang) {
      return;
    }
    localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
    // Number, currency and date formats are bound to the locale chosen at startup,
    // so reload to apply the new language consistently across every screen.
    this.reloadPage();
  }

  /** Separated so tests can stub the full page reload. */
  reloadPage(): void {
    window.location.reload();
  }

  /** Applies the theme saved on the account once per signed-in user. */
  private syncThemeFromAccount(): void {
    const accountId = this.currentUser?.id ?? null;
    if (!accountId || accountId === this.themeSyncedFor || !this.authService.isAuthenticated()) {
      return;
    }
    this.themeSyncedFor = accountId;
    this.injector.get(UserManagementService).getUserPreferences().subscribe({
      next: preferences => this.themeService.apply(preferences.theme),
      error: () => { /* keep the theme stored in this browser */ }
    });
  }

  /** Toolbar shortcut: flips between light and dark and saves it as the account preference. */
  toggleTheme(): void {
    const next = this.themeService.isDark() ? 'light' : 'dark';
    this.themeService.apply(next);
    const users = this.injector.get(UserManagementService);
    users.getUserPreferences().subscribe({
      next: preferences => users.updateUserPreferences({ ...preferences, theme: next }).subscribe({
        error: () => { /* the theme still applies in this browser */ }
      }),
      error: () => { /* the theme still applies in this browser */ }
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
