import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
  };
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly TOKEN_KEY = 'indezy_token';
  private readonly USER_KEY = 'indezy_user';

  private currentUserSubject = new BehaviorSubject<any>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    // Mock login for development - replace with real API call when backend is ready
    return new Observable(observer => {
      setTimeout(() => {
        if (credentials.email && credentials.password) {
          const mockResponse: LoginResponse = {
            token: 'mock-jwt-token-' + Date.now(),
            user: {
              id: 1,
              email: credentials.email,
              firstName: 'John',
              lastName: 'Doe'
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
      }, 1000);
    });
  }

  register(userData: RegisterRequest): Observable<LoginResponse> {
    // Mock register for development - replace with real API call when backend is ready
    return new Observable(observer => {
      setTimeout(() => {
        if (userData.email && userData.password && userData.firstName && userData.lastName) {
          const mockResponse: LoginResponse = {
            token: 'mock-jwt-token-' + Date.now(),
            user: {
              id: 1,
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
      }, 1000);
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

  getUser(): any {
    return this.getUserFromStorage();
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) {
      return false;
    }

    // For mock tokens, just check if token exists and user exists
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

  private setUser(user: any): void {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  private getUserFromStorage(): any {
    const userStr = localStorage.getItem(this.USER_KEY);
    return userStr ? JSON.parse(userStr) : null;
  }
}
