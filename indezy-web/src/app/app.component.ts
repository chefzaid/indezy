import { Component, DestroyRef, OnInit, ChangeDetectionStrategy, HostListener, ViewChild, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { Observable, filter, map } from 'rxjs';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';

import { NavigationEnd, Router, RouterOutlet, RouterModule } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService, HOME_PAGE_PATHS } from './services/auth/auth.service';
import { LANGUAGE_STORAGE_KEY, getSavedLanguage } from './shared/locale/app-locale';
import { ConnectivityService } from './shared/services/connectivity.service';
import { ThemeService } from './shared/theme/theme.service';
import { LogoComponent } from './shared/components/logo/logo.component';
import { UserManagementService } from './services/user-management/user-management.service';
import { KeyboardShortcutsService } from './shared/shortcuts/keyboard-shortcuts.service';
import { OfferAlertsService } from './services/offer/offer-alerts.service';
import { NetworkNoticesService } from './services/network/network-notices.service';
import { CapabilitiesService } from './services/admin/capabilities.service';
import { FeatureFlag } from './models';
import { MessengerDockComponent } from './components/network/messenger-dock/messenger-dock.component';
import { HelpPanelComponent, helpArticleFor } from './components/help/help-panel.component';

interface MenuItem {
  labelKey: string;
  route: string;
  icon: string;
  /** A count shown next to the item when not zero. */
  badge$?: Observable<number>;
  /** Shown while this feature is on. */
  feature?: FeatureFlag;
  /** Shown to administrators only. */
  admin?: boolean;
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
    TranslateModule,
    AsyncPipe,
    LogoComponent,
    MessengerDockComponent
],
    templateUrl: './app.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  readonly shortcuts = inject(KeyboardShortcutsService);

  @HostListener('document:keydown', ['$event'])
  onShortcut(event: KeyboardEvent): void { this.shortcuts.handle(event); }

  title = 'Indezy';
  currentUser: { id: number; email: string; firstName: string; lastName: string; role?: string } | null = null;
  currentLang = 'fr';
  /** Below this width the navigation becomes an overlay drawer instead of a permanent side panel. */
  static readonly COMPACT_LAYOUT_QUERY = '(max-width: 1023.98px)';
  /** Where this browser remembers the menu folded to its icons. */
  static readonly MENU_COLLAPSED_KEY = 'indezy.menuCollapsed';
  isCompactLayout = false;
  /** The side menu shows its icons only, each name on hover; kept in this browser. */
  menuCollapsed = AppComponent.savedCollapsed();

  private readonly destroyRef = inject(DestroyRef);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly userManagementService = inject(UserManagementService);
  private readonly offerAlerts = inject(OfferAlertsService);
  private readonly networkNotices = inject(NetworkNoticesService);
  private readonly capabilities = inject(CapabilitiesService);
  readonly themeService = inject(ThemeService);
  @ViewChild('sidenav') sidenav?: MatSidenav;
  readonly connectivity = inject(ConnectivityService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  /** The presentation page shows without the app's menu, signed in or not. */
  readonly onHomePage = toSignal(this.router.events.pipe(
    filter(event => event instanceof NavigationEnd),
    map(event => HOME_PAGE_PATHS.includes((event as NavigationEnd).urlAfterRedirects.split(/[?#]/)[0]))
  ), { initialValue: false });
  /** The Messages page, which the messenger corner leaves to itself: no room is kept for the corner there. */
  readonly onMessagesPage = toSignal(this.router.events.pipe(
    filter(event => event instanceof NavigationEnd),
    map(event => (event as NavigationEnd).urlAfterRedirects.startsWith('/network/messages'))
  ), { initialValue: false });
  /** Account id whose saved theme preference has been applied. */
  private themeSyncedFor: number | null = null;

  menuItems: MenuItem[] = [
    { labelKey: 'nav.dashboard', route: '/dashboard', icon: 'dashboard' },
    { labelKey: 'nav.calendar', route: '/calendar', icon: 'calendar_month' },
    { labelKey: 'nav.offers', route: '/offers', icon: 'travel_explore', badge$: this.offerAlerts.newOffers$, feature: 'OFFER_FEED' },
    { labelKey: 'nav.projects', route: '/projects', icon: 'work' },
    { labelKey: 'nav.network', route: '/network', icon: 'diversity_3', badge$: this.networkNotices.total$, feature: 'SOCIAL_NETWORK' },
    { labelKey: 'nav.clients', route: '/clients', icon: 'business' },
    { labelKey: 'nav.contacts', route: '/contacts', icon: 'contacts' },
    { labelKey: 'nav.sources', route: '/sources', icon: 'source' },
    { labelKey: 'nav.review', route: '/review', icon: 'auto_graph', feature: 'YEARLY_REVIEW' },
    { labelKey: 'nav.activityReports', route: '/activity-reports', icon: 'edit_calendar' },
    { labelKey: 'nav.invoices', route: '/invoices', icon: 'receipt_long' },
    { labelKey: 'nav.quotes', route: '/quotes', icon: 'request_quote' },
    { labelKey: 'nav.expenses', route: '/expenses', icon: 'payments' },
    { labelKey: 'nav.documents', route: '/documents', icon: 'folder_shared' },
    { labelKey: 'nav.archive', route: '/archive', icon: 'inventory_2' },
    { labelKey: 'nav.help', route: '/help', icon: 'help' },
    { labelKey: 'nav.admin', route: '/admin', icon: 'admin_panel_settings', admin: true }
  ];

  /** The messenger corner shows while the member network is on, on a screen wide enough for the menu to stay. */
  get messengerShown(): boolean {
    return this.capabilities.snapshot().features.SOCIAL_NETWORK === true && !this.isCompactLayout;
  }

  /** The items the account may open: features left on, and the admin console for administrators. */
  get visibleMenuItems(): MenuItem[] {
    const capabilities = this.capabilities.snapshot();
    return this.menuItems.filter(item => (!item.feature || capabilities.features[item.feature] !== false) &&
      (!item.admin || capabilities.admin));
  }

  constructor(
    public authService: AuthService,
    private readonly translate: TranslateService
  ) {
    this.currentLang = getSavedLanguage();
    this.translate.use(this.currentLang);
  }

  /** Counts new offers in the menu unless the account turned the badge off; on when its settings cannot be read. */
  private startOfferAlertsIfWanted(): void {
    this.userManagementService.getNotificationSettings().subscribe({
      next: settings => {
        if (settings.newOffersBadge !== false) {
          this.offerAlerts.start();
        }
      },
      error: () => this.offerAlerts.start()
    });
  }

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      this.syncThemeFromAccount();
      if (user && this.authService.isAuthenticated()) {
        this.capabilities.refresh().subscribe(capabilities => {
          if (capabilities.features.OFFER_FEED !== false) {
            this.startOfferAlertsIfWanted();
          }
          if (capabilities.features.SOCIAL_NETWORK === true) {
            this.networkNotices.start();
          }
        });
      } else {
        this.capabilities.clear();
        this.offerAlerts.stop();
        this.networkNotices.stop();
      }
    });
    this.breakpointObserver.observe(AppComponent.COMPACT_LAYOUT_QUERY)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(state => this.isCompactLayout = state.matches);
  }

  /** Whether the menu shows as a rail of icons: folded, on a screen wide enough for the menu to stay. */
  get railed(): boolean {
    return this.menuCollapsed && !this.isCompactLayout;
  }

  /** The burger: folds the side menu to its icons, or unfolds it; on a narrow screen, opens or closes the drawer. */
  toggleMenu(): void {
    if (this.isCompactLayout) {
      void this.sidenav?.toggle();
      return;
    }
    this.menuCollapsed = !this.menuCollapsed;
    try {
      localStorage.setItem(AppComponent.MENU_COLLAPSED_KEY, String(this.menuCollapsed));
    } catch {
      // Not remembered in this browser; the menu still folds.
    }
  }

  private static savedCollapsed(): boolean {
    try {
      return localStorage.getItem(AppComponent.MENU_COLLAPSED_KEY) === 'true';
    } catch {
      return false;
    }
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
    this.userManagementService.getUserPreferences().subscribe({
      next: preferences => this.themeService.apply(preferences.theme),
      error: () => { /* keep the theme stored in this browser */ }
    });
  }

  /** Toolbar shortcut: flips between light and dark and saves it as the account preference. */
  /** Opens the help of the page in use in a panel at the side. */
  openHelp(): void {
    this.dialog.open(HelpPanelComponent, { data: helpArticleFor(globalThis.location?.pathname ?? this.router.url), position: { right: '0', top: '0' }, height: '100vh',
      width: '480px', maxWidth: '100vw', panelClass: 'help-panel-dialog', autoFocus: 'dialog' });
  }

  toggleTheme(): void {
    const next = this.themeService.isDark() ? 'light' : 'dark';
    this.themeService.apply(next);
    this.userManagementService.getUserPreferences().subscribe({
      next: preferences => this.userManagementService.updateUserPreferences({ ...preferences, theme: next }).subscribe({
        error: () => { /* the theme still applies in this browser */ }
      }),
      error: () => { /* the theme still applies in this browser */ }
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
