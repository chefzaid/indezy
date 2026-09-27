import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/** Resolve shared links through the normal, authenticated project detail route. */
export const projectLinkGuard: CanActivateFn = route => {
  const values = route.queryParamMap.getAll('project');
  if (values.length === 0) { return true; }
  const id = values[0];
  const valid = values.length === 1 && /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id));
  return inject(Router).createUrlTree(valid ? ['/projects', id] : ['/404']);
};
