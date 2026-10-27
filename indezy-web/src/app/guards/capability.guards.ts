import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { FeatureFlag } from '../models';
import { CapabilitiesService } from '../services/admin/capabilities.service';

/** Opens a feature's pages only while it is on; otherwise back to the dashboard. */
export function featureGuard(flag: FeatureFlag): CanActivateFn {
  return () => {
    const router = inject(Router);
    return inject(CapabilitiesService).isEnabled(flag).pipe(map(enabled => enabled || router.parseUrl('/dashboard')));
  };
}

/** Opens the admin console to administrators only. */
export const adminGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(CapabilitiesService).get().pipe(map(capabilities => capabilities.admin || router.parseUrl('/dashboard')));
};
