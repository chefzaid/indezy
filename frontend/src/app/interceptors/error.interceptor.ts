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
        router.navigate(['/unauthorized']);
      } else if (error.status === 0) {
        // Network error
        console.error('Network error occurred:', error);
      } else {
        // Other HTTP errors
        console.error('HTTP error occurred:', error);
      }

      return throwError(() => error);
    })
  );
};
