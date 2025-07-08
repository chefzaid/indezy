import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
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

  private readonly currentUserSubject = new BehaviorSubject<User | null>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    // TODO: Replace mock login with real API call when backend is ready
    return new Observable(observer => {
      setTimeout(() => {
        if (credentials.email && credentials.password) {
          const mockResponse: LoginResponse = {
            token: 'mock-jwt-token-' + Date.now(), // TODO: Replace with real JWT token from backend
            user: {
              id: 1, // TODO: Get real user ID from backend response
              email: credentials.email,
              firstName: 'John', // TODO: Get real user data from backend response
              lastName: 'Doe' // TODO: Get real user data from backend response
            }
          };
          this.setToken(mockResponse.token);
          this.setUser(mockResponse.user);
          this.currentUserSubject.next(mockResponse.user);
          observer.next(mockResponse);
          observer.complete();
        } else {
          observer.error({ status: 401, message: 'Invalid credentials' });
        }
      }, 1000); // TODO: Remove artificial delay when using real API
    });
  }

  register(userData: RegisterRequest): Observable<LoginResponse> {
    // TODO: Replace mock register with real API call when backend is ready
    return new Observable(observer => {
      setTimeout(() => {
        if (userData.email && userData.password && userData.firstName && userData.lastName) {
          const mockResponse: LoginResponse = {
            token: 'mock-jwt-token-' + Date.now(), // TODO: Replace with real JWT token from backend
            user: {
              id: 1, // TODO: Get real user ID from backend response
              email: userData.email,
              firstName: userData.firstName,
              lastName: userData.lastName
            }
          };
          this.setToken(mockResponse.token);
          this.setUser(mockResponse.user);
          this.currentUserSubject.next(mockResponse.user);
          observer.next(mockResponse);
          observer.complete();
        } else {
          observer.error({ status: 400, message: 'Invalid user data' });
        }
      }, 1000); // TODO: Remove artificial delay when using real API
    });
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
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

    // TODO: Remove mock token handling when real JWT validation is implemented
    if (token.startsWith('mock-jwt-token-')) {
      return this.getUser() !== null;
    }

    // Check if real JWT token is expired (basic check)
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
