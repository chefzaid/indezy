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
import { BehaviorSubject } from 'rxjs';
import { OfferAlertsService } from './services/offer/offer-alerts.service';

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

  beforeEach(async () => {
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
});
