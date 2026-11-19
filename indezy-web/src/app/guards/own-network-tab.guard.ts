import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, catchError, map, of } from 'rxjs';

import { NetworkService } from '../services/network/network.service';

/**
 * The saved posts and the drafts are tabs of the reader's own page (`/m/<handle>?tab=saved`): their former addresses
 * (`/network/saved`, `/network/drafts`) lead there, and to the feed for a visitor who has not joined the network.
 */
export function ownNetworkTab(tab: 'saved' | 'drafts'): CanActivateFn {
  return (): Observable<UrlTree> => {
    const router = inject(Router);
    return inject(NetworkService).membership(true).pipe(
      map(membership => membership.joined ? router.createUrlTree(['/m', membership.handle], { queryParams: { tab } }) :
        router.createUrlTree(['/network'])),
      catchError(() => of(router.createUrlTree(['/network']))));
  };
}
