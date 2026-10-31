import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** A company of the French public directory. */
export interface Company {
  siren: string;
  name: string;
  address?: string | null;
  postalCode?: string | null;
  city?: string | null;
  nafCode?: string | null;
  legalForm?: string | null;
  vatNumber?: string | null;
  closed: boolean;
}

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly http = inject(HttpClient);

  /** Up to five companies by name or SIREN; none under three characters. */
  search(query: string): Observable<Company[]> {
    return this.http.get<Company[]>(`${environment.apiUrl}/companies/search`, { params: new HttpParams().set('q', query) });
  }
}
