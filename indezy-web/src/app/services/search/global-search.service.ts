import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GlobalSearchResult } from '../../models/search.models';

/** Searches the workspace's opportunities, clients, contacts and notes at once. */
@Injectable({ providedIn: 'root' })
export class GlobalSearchService {
  private readonly http = inject(HttpClient);

  search(query: string): Observable<GlobalSearchResult> {
    return this.http.get<GlobalSearchResult>(`${environment.apiUrl}/search`, { params: { q: query } });
  }
}
