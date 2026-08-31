import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, firstValueFrom, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, RegisterRequest, User } from '../../models/auth.models';

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

  async initializeSso(enabled = environment.production): Promise<void> {
    if (!enabled) {
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
}
