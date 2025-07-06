import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // Don't override existing Authorization header
  if (req.headers.has('Authorization')) {
    return next(req);
  }

  try {
    const token = authService.getToken();

    if (token) {
      const authReq = req.clone({
        headers: req.headers.set('Authorization', `Bearer ${token}`)
      });
      return next(authReq);
    }
  } catch (error) {
    // If token service fails, continue without token
    console.warn('Failed to get auth token:', error);
  }

  return next(req);
};
