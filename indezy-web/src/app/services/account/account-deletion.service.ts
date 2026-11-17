import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

/** Deleting one's own account: on hold at once, deleted for good after 30 days unless restored by signing in. */
@Injectable({
  providedIn: 'root'
})
export class AccountDeletionService {
  private readonly http = inject(HttpClient);

  /** Puts the account on hold, its email typed to confirm; answers the day it will be deleted. */
  request(confirmation: string): Observable<{ purgeOn: string }> {
    return this.http.post<{ purgeOn: string }>(`${environment.apiUrl}/users/me/deletion`, { confirmation });
  }
}
