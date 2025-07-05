import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Unauthorized - redirect to login
        authService.logout();
        router.navigate(['/login']);
      } else if (error.status === 403) {
        // Forbidden - redirect to unauthorized page
        router.navigate(['/404']);
      } else if (error.status === 404) {
        // Not found - redirect to 404 page
        router.navigate(['/404']);
      } else if (error.status === 0) {
        // Network error
        console.error('Network error occurred:', error);
        router.navigate(['/error']);
      } else if (error.status >= 500) {
        // Server errors - redirect to error page
        console.error('Server error occurred:', error);
        router.navigate(['/error']);
      } else {
        // Other HTTP errors
        console.error('HTTP error occurred:', error);
      }

      return throwError(() => error);
    })
  );
};
