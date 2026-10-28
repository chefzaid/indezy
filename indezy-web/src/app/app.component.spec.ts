import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { AuthService } from './services/auth/auth.service';
import { RouterTestingModule } from '@angular/router/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { UserManagementService } from './services/user-management/user-management.service';
import { OfferAlertsService } from './services/offer/offer-alerts.service';
import { CapabilitiesService, DEFAULT_CAPABILITIES } from './services/admin/capabilities.service';
import { Capabilities } from './models';

describe('AppComponent', () => {
  let mockAuthService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    mockAuthService = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'logout'], {
      currentUser$: { subscribe: jasmine.createSpy() }
    });

    await TestBed.configureTestingModule({
      imports: [
        AppComponent,
        RouterTestingModule,
        NoopAnimationsModule,
        MatToolbarModule,
        MatButtonModule,
        MatIconModule,
        MatSidenavModule,
        MatListModule,
        MatMenuModule,
        TranslateModule.forRoot()
      ],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('says when the browser is offline', () => {
    mockAuthService.isAuthenticated.and.returnValue(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-offline-banner]')).toBeNull();
    window.dispatchEvent(new Event('offline'));
    fixture.detectChanges();
    expect(element.querySelector('[data-offline-banner]')!.getAttribute('role')).toBe('status');
    window.dispatchEvent(new Event('online'));
    fixture.detectChanges();
    expect(element.querySelector('[data-offline-banner]')).toBeNull();
  });

  it('should have title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('Indezy');
  });
});

describe('AppComponent offer alerts', () => {
  let user$: BehaviorSubject<{ id: number; email: string; firstName: string; lastName: string } | null>;
  let count$: BehaviorSubject<number>;
  let alerts: jasmine.SpyObj<OfferAlertsService>;
  let capabilities: Capabilities;
  let read: Capabilities | null;
  let badge: boolean | 'error';
  const capabilitiesService = {
    refresh: () => {
      read = capabilities;
      return of(capabilities);
    },
    clear: () => read = null,
    snapshot: () => read ?? DEFAULT_CAPABILITIES
  };

  beforeEach(async () => {
    capabilities = DEFAULT_CAPABILITIES;
    read = null;
    badge = true;
    user$ = new BehaviorSubject<{ id: number; email: string; firstName: string; lastName: string } | null>(null);
    count$ = new BehaviorSubject(0);
    alerts = jasmine.createSpyObj('OfferAlertsService', ['start', 'stop', 'refresh'], { newOffers$: count$.asObservable() });
    const auth = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'logout'], { currentUser$: user$.asObservable() });
    auth.isAuthenticated.and.callFake(() => user$.value !== null);
    await TestBed.configureTestingModule({
      imports: [AppComponent, RouterTestingModule, NoopAnimationsModule, TranslateModule.forRoot()],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: OfferAlertsService, useValue: alerts },
        { provide: CapabilitiesService, useValue: capabilitiesService },
        { provide: UserManagementService, useValue: {
          getNotificationSettings: () => badge === 'error' ? throwError(() => new Error('offline')) :
            of({ weeklyReports: false, weeklyDigestSections: [], bookingEmails: true, newOffersBadge: badge, emailsAvailable: true }),
          getUserPreferences: () => of({})
        } },
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('counts new offers while signed in and shows them on Offers', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(alerts.stop).toHaveBeenCalled();

    user$.next({ id: 1, email: 'john@example.com', firstName: 'John', lastName: 'Doe' });
    fixture.detectChanges();
    expect(alerts.start).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-nav-badge]')).toBeNull();

    count$.next(3);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-nav-badge]').textContent.trim()).toBe('3');
    count$.next(240);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-nav-badge]').textContent.trim()).toBe('99+');
  });

  function menuRoutes(fixture: { nativeElement: HTMLElement }): (string | null)[] {
    return Array.from(fixture.nativeElement.querySelectorAll('mat-sidenav a[href]')).map(link => link.getAttribute('href'));
  }

  it('hides the features turned off, and shows the admin console to administrators only', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    user$.next({ id: 1, email: 'john@example.com', firstName: 'John', lastName: 'Doe' });
    fixture.detectChanges();
    expect(menuRoutes(fixture)).toEqual(['/dashboard', '/calendar', '/offers', '/projects', '/clients', '/contacts', '/sources',
      '/review', '/activity-reports', '/invoices', '/documents', '/archive']);

    capabilities = { admin: true, features: { ...DEFAULT_CAPABILITIES.features, OFFER_FEED: false, YEARLY_REVIEW: false } };
    alerts.start.calls.reset();
    user$.next({ id: 2, email: 'chief@example.com', firstName: 'Chief', lastName: 'Admin' });
    fixture.detectChanges();
    expect(alerts.start).not.toHaveBeenCalled();
    expect(menuRoutes(fixture)).toContain('/admin');
    expect(menuRoutes(fixture)).not.toContain('/offers');
    expect(menuRoutes(fixture)).not.toContain('/review');

    user$.next(null);
    fixture.detectChanges();
    expect(read).toBeNull();
  });

  it('leaves the badge off when the account turned it off, and on when its settings cannot be read', () => {
    badge = false;
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    user$.next({ id: 1, email: 'john@example.com', firstName: 'John', lastName: 'Doe' });
    expect(alerts.start).not.toHaveBeenCalled();

    badge = 'error';
    user$.next({ id: 2, email: 'jane@example.com', firstName: 'Jane', lastName: 'Doe' });
    expect(alerts.start).toHaveBeenCalled();
  });
});
