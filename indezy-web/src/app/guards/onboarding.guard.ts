import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth/auth.service';

/** Sends a new account to its welcome steps until it finishes or skips them. */
export const onboardingGuard: CanActivateFn = () => {
  return inject(AuthService).getUser()?.onboardingPending ? inject(Router).parseUrl('/welcome') : true;
};
