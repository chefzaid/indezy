import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, catchError, map, of, switchMap } from 'rxjs';

import { NetworkService } from '../../../services/network/network.service';
import { ConfidentialityChoice, ConfidentialityData, ConfidentialityDialogComponent } from './confidentiality-dialog.component';

/**
 * Checks a post or a comment for the workspace's client, ESN and contact names before it goes out: none found (or the
 * check unavailable), it is published as it is; otherwise the member anonymizes it, publishes it anyway, or cancels (none).
 */
@Injectable({ providedIn: 'root' })
export class ConfidentialityGuardService {
  private readonly network = inject(NetworkService);
  private readonly dialog = inject(MatDialog);

  review(text: string): Observable<ConfidentialityChoice | null> {
    return this.network.checkConfidentiality(text).pipe(
      catchError(() => of([])),
      switchMap(matches => matches.length === 0 ? of<ConfidentialityChoice>({ action: 'publish', text }) :
        this.dialog.open<ConfidentialityDialogComponent, ConfidentialityData, ConfidentialityChoice>(ConfidentialityDialogComponent,
          { data: { text, matches } }).afterClosed().pipe(map(choice => choice ?? null)))
    );
  }
}
