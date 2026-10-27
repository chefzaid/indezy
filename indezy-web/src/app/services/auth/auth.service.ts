import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, firstValueFrom, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, RegisterRequest, User } from '../../models/auth.models';
import { clearOfflineData } from '../../shared/utils/offline-data';

/**
 * Pages opened without an Indezy account: an ESN validating an activity report or opening shared documents, an email's
 * unsubscribe link, a public profile.
 */
export const PUBLIC_PAGE_PATHS = ['/activity-report', '/documents-pack', '/emails/unsubscribe', '/p'];

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly TOKEN_KEY = 'indezy_token';
  private readonly USER_KEY = 'indezy_user';
  private readonly SSO_ATTEMPT_KEY = 'indezy.keycloak-sso-attempt';
  private readonly SSO_START_URL = 'https://keycloak.swirlit.dev/oauth2/start';

  private readonly currentUserSubject = new BehaviorSubject<User | null>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/login`, credentials).pipe(
      tap((response: LoginResponse) => {
        this.setToken(response.token);
        this.setUser(response.user);
        this.currentUserSubject.next(response.user);
      })
    );
  }

  register(userData: RegisterRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/register`, userData).pipe(
      tap((response: LoginResponse) => {
        this.setToken(response.token);
        this.setUser(response.user);
        this.currentUserSubject.next(response.user);
      })
    );
  }

  logout(): void {
    this.endSession(this.getToken());
    clearOfflineData().catch(() => undefined);
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    if (environment.production) {
      sessionStorage.setItem(this.SSO_ATTEMPT_KEY, 'pending');
      const returnUrl = `${window.location.origin}/`;
      window.location.assign(
        `https://keycloak.swirlit.dev/oauth2/sign_out?rd=${encodeURIComponent(returnUrl)}`
      );
    } else {
      this.router.navigate(['/login']);
    }
  }

  /** Exchanges the Keycloak session for an Indezy one, except on public pages, which must not send visitors to sign in. */
  async initializeSso(enabled = environment.production, path = window.location.pathname): Promise<void> {
    if (!enabled || PUBLIC_PAGE_PATHS.some(publicPath => path === publicPath || path.startsWith(`${publicPath}/`))) {
      return;
    }

    const sessionExchanged = await firstValueFrom(
      this.http.get<LoginResponse>(`${this.API_URL}/sso`).pipe(
        tap((response: LoginResponse) => {
          this.setToken(response.token);
          this.setUser(response.user);
          this.currentUserSubject.next(response.user);
          sessionStorage.removeItem(this.SSO_ATTEMPT_KEY);
        }),
        map(() => true),
        catchError(() => of(false))
      )
    );

    if (!sessionExchanged && sessionStorage.getItem(this.SSO_ATTEMPT_KEY) !== 'pending') {
      this.startSsoLogin();
    }
  }

  startSsoLogin(returnUrl = window.location.href): void {
    sessionStorage.setItem(this.SSO_ATTEMPT_KEY, 'pending');
    const loginUrl = `${this.SSO_START_URL}?rd=${encodeURIComponent(returnUrl)}`;
    window.location.assign(loginUrl);
  }

  /** Ends the welcome steps, finished or skipped, so the dashboard no longer leads to them. */
  completeOnboarding(): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/users/onboarding/complete`, null).pipe(
      tap(() => {
        const user = this.getUserFromStorage();
        if (user) {
          const welcomed = { ...user, onboardingPending: false };
          this.setUser(welcomed);
          this.currentUserSubject.next(welcomed);
        }
      })
    );
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getUser(): User | null {
    return this.getUserFromStorage();
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) {
      return false;
    }

    // Check if JWT token is expired (basic check)
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Math.floor(Date.now() / 1000);
      return payload.exp > currentTime;
    } catch {
      return false;
    }
  }

  private setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  private setUser(user: User): void {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  private getUserFromStorage(): User | null {
    const userStr = localStorage.getItem(this.USER_KEY);
    return userStr ? JSON.parse(userStr) : null;
  }

  /**
   * Ends the token's session on the server, so the token stops working even if copied. A keepalive request survives
   * the redirect to Keycloak's sign-out that follows; a failure leaves the session to expire with its token.
   */
  private endSession(token: string | null): void {
    if (!token) {
      return;
    }
    try {
      fetch(`${environment.apiUrl}/users/sessions/current`, {
        method: 'DELETE', keepalive: true, headers: { Authorization: `Bearer ${token}` }
      }).catch(() => undefined);
    } catch {
      // No fetch (an old browser): the session expires with its token.
    }
  }
}
