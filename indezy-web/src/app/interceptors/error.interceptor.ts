import { HttpContextToken, HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth/auth.service';
import { NotificationService } from '../services/notification/notification.service';

/**
 * Set on requests made in the background (a badge counting every few minutes), whose failures the user did not ask to
 * hear about, and on requests whose caller tells the failure itself: they are not reported here. An expired session
 * still signs out.
 */
export const SILENT_HTTP_ERRORS = new HttpContextToken<boolean>(() => false);

/** Authentication endpoints report bad credentials themselves; a 401 there is not an expired session. */
const AUTH_ENDPOINT = /\/auth\/(login|register|sso)(\?|$)/;

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);
  // Resolved lazily: TranslateService (a NotificationService dependency) loads its
  // translation files through HttpClient, so injecting it eagerly here is circular.
  const injector = inject(Injector);
  const silent = req.context.get(SILENT_HTTP_ERRORS);
  const notify = (messageKey: string): void => {
    if (!silent) {
      injector.get(NotificationService).error(messageKey, 5000);
    }
  };

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
        // Offline, reads come from what the app kept; anything else waits for the connection.
        notify(globalThis.navigator?.onLine === false ? 'errors.offline' : 'errors.networkError');
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
