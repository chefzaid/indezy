import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, firstValueFrom, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AccountOnHold, LoginRequest, LoginResponse, RegisterRequest, User } from '../../models/auth.models';
import { clearOfflineData } from '../../shared/utils/offline-data';

/**
 * Pages opened without an Indezy account: an ESN validating an activity report, opening shared documents or booking a
 * call, an email's unsubscribe link, a public profile.
 */
export const PUBLIC_PAGE_PATHS = ['/activity-report', '/book', '/documents-pack', '/shared-cv', '/emails/unsubscribe', '/forgot-password', '/p', '/reset-password',
  '/testimonial', '/verify-email'];

/** The presentation page's addresses: the session is exchanged there to know who is signed in, never asked for. */
export const HOME_PAGE_PATHS = ['/', '/en'];

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly TOKEN_KEY = 'indezy_token';
  private readonly USER_KEY = 'indezy_user';
  private readonly SSO_ATTEMPT_KEY = 'indezy.keycloak-sso-attempt';
  /** How the current session was opened: 'sso' through Keycloak, 'password' on Indezy's own screen. */
  private readonly SIGN_IN_KEY = 'indezy.sign-in';
  private readonly SSO_START_URL = 'https://keycloak.swirlit.dev/oauth2/start';

  private readonly currentUserSubject = new BehaviorSubject<User | null>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/login`, credentials).pipe(
      tap((response: LoginResponse) => this.openSession(response))
    );
  }

  register(userData: RegisterRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/register`, userData).pipe(
      tap((response: LoginResponse) => {
        // In production the email is confirmed first: no session until its link is followed.
        if (!response.emailVerificationRequired) {
          this.openSession(response);
        }
      })
    );
  }

  /** Confirms the email from its link, and signs in. */
  verifyEmail(token: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/verify-email`, { token }).pipe(tap(response => this.openSession(response)));
  }

  /** Sends the confirmation link again (the answer is the same whether the address has an account or not). */
  resendVerification(email: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/verify-email/resend`, { email });
  }

  /** Sends a link to set a new password (the answer is the same whether the address has an account or not). */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/password/forgot`, { email });
  }

  /** Sets the new password from the reset link, and signs in. */
  resetPassword(token: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/password/reset`, { token, password }).pipe(tap(response => this.openSession(response)));
  }

  private openSession(response: LoginResponse, method: 'password' | 'sso' = 'password'): void {
    if (!response.token) {
      return;
    }
    this.setToken(response.token);
    this.setUser(response.user);
    this.currentUserSubject.next(response.user);
    localStorage.setItem(this.SIGN_IN_KEY, method);
  }

  logout(): void {
    this.endSession(this.getToken());
    clearOfflineData().catch(() => undefined);
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    const throughSso = localStorage.getItem(this.SIGN_IN_KEY) === 'sso';
    localStorage.removeItem(this.SIGN_IN_KEY);
    // Signed in through Keycloak, the platform's session ends too; with a password, the login screen is enough.
    if (environment.production && throughSso) {
      sessionStorage.setItem(this.SSO_ATTEMPT_KEY, 'pending');
      const returnUrl = `${window.location.origin}/`;
      window.location.assign(
        `https://keycloak.swirlit.dev/oauth2/sign_out?rd=${encodeURIComponent(returnUrl)}`
      );
    } else {
      this.router.navigate(['/login']);
    }
  }

  /**
   * Exchanges a Keycloak session, when the browser has one, for an Indezy one, quietly: a visitor without one stays on
   * Indezy's own screens and signs in with a password or **Continue with SSO**. A session opened with a password is left
   * as it is; public pages make no exchange.
   */
  async initializeSso(enabled = environment.production, path = window.location.pathname): Promise<void> {
    if (!enabled || PUBLIC_PAGE_PATHS.some(publicPath => path === publicPath || path.startsWith(`${publicPath}/`))) {
      return;
    }
    if (this.isAuthenticated() && localStorage.getItem(this.SIGN_IN_KEY) === 'password') {
      return;
    }

    let held: string | null = null;
    const sessionExchanged = await firstValueFrom(
      this.http.get<LoginResponse>(`${this.API_URL}/sso`).pipe(
        tap((response: LoginResponse) => {
          this.openSession(response, 'sso');
          sessionStorage.removeItem(this.SSO_ATTEMPT_KEY);
        }),
        map(() => true),
        catchError((error: { status?: number; error?: Partial<AccountOnHold> }) => {
          held = error?.status === 409 && error.error?.accountOnHold ? error.error.purgeOn ?? '' : null;
          return of(false);
        })
      )
    );

    // An account on hold signs in to nothing but the choice to restore it.
    if (held !== null) {
      sessionStorage.removeItem(this.SSO_ATTEMPT_KEY);
      await this.router.navigate(['/login'], { queryParams: { onHold: held, sso: 1 } });
      return;
    }

    if (!sessionExchanged) {
      sessionStorage.removeItem(this.SSO_ATTEMPT_KEY);
    }
  }

  /** Takes the account signed in through Keycloak off hold, and opens its session. */
  restoreSso(): Observable<LoginResponse> {
    return this.http.get<LoginResponse>(`${this.API_URL}/sso`, { params: { restore: true } }).pipe(
      tap((response: LoginResponse) => this.openSession(response, 'sso'))
    );
  }

  /** Forgets the session here, which the server already ended (the account went on hold). */
  forgetSession(): void {
    clearOfflineData().catch(() => undefined);
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
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
