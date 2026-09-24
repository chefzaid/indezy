import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth/auth.service';
import { NotificationService } from '../services/notification/notification.service';

/** Authentication endpoints report bad credentials themselves; a 401 there is not an expired session. */
const AUTH_ENDPOINT = /\/auth\/(login|register|sso)(\?|$)/;

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);
  // Resolved lazily: TranslateService (a NotificationService dependency) loads its
  // translation files through HttpClient, so injecting it eagerly here is circular.
  const injector = inject(Injector);
  const notify = (messageKey: string): void => injector.get(NotificationService).error(messageKey, 5000);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        if (!AUTH_ENDPOINT.test(req.url)) {
          // Expired or invalid session - sign out and return to the login page.
          authService.logout();
          router.navigate(['/login']);
        }
      } else if (error.status === 403) {
        notify('errors.forbidden');
      } else if (error.status === 404) {
        // Resource-level misses belong to the requesting component. Redirecting
        // globally would replace an otherwise healthy authenticated page.
        console.error('Resource not found:', error);
      } else if (error.status === 0) {
        // Keep the user on the current screen; the requesting component decides how to recover.
        console.error('Network error occurred:', error);
        notify('errors.networkError');
      } else if (error.status >= 500) {
        console.error('Server error occurred:', error);
        notify('errors.serverError');
      } else {
        // Other HTTP errors
        console.error('HTTP error occurred:', error);
      }

      return throwError(() => error);
    })
  );
};
